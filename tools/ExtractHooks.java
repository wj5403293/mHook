import com.google.common.io.ByteStreams;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.instruction.formats.ArrayPayload;
import org.jf.dexlib2.iface.instruction.SwitchPayload;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.OffsetInstruction;
import org.jf.dexlib2.iface.instruction.NarrowLiteralInstruction;
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ThreeRegisterInstruction;
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction;
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * dexlib2 提取器：把 APK 里 XP 模块的 hook 注册点 + 回调方法体抽取成
 * 便于 AI/人工阅读的文本，供外部智能分析生成精确的 mHook 配置。
 * 与 cn.mhook.analyze.DexAnalyzer 完全独立，只依赖 dexlib2 原始指令。
 */
public class ExtractHooks {

    static List<DexBackedDexFile> dexList = new ArrayList<>();
    static Map<String, ClassDef> classes = new HashMap<>();
    static PrintStream out;

    public static void main(String[] args) throws Exception {
        String apk = args[0];
        String outPath = args.length > 1 ? args[1] : null;
        PrintStream fileOs = outPath == null ? System.out : new PrintStream(new FileOutputStream(outPath), true, "UTF-8");
        out = fileOs;

        ZipFile zf = new ZipFile(apk);
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (!e.getName().endsWith(".dex")) continue;
            try {
                DexBackedDexFile dex = new DexBackedDexFile(Opcodes.forApi(28), ByteStreams.toByteArray(zf.getInputStream(e)));
                dexList.add(dex);
                for (ClassDef cls : dex.getClasses()) classes.put(cls.getType(), cls);
            } catch (Throwable t) { out.println("# skip dex " + e.getName() + ": " + t); }
        }
        zf.close();

        String entry = findXposedInit(apk);
        out.println("##### META");
        out.println("apk=" + apk);
        out.println("xposed_init=" + entry);
        out.println("dex_count=" + dexList.size());
        out.println("class_count=" + classes.size());

        // 1) 注册点扫描：任何方法里出现 Xposed hook API 都算
        Set<String> regMethods = new LinkedHashSet<>();
        Set<String> cbClasses = new LinkedHashSet<>();
        for (ClassDef cls : classes.values()) {
            for (Method m : cls.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) continue;
                ScanCtx ctx = scanMethod(cls, m, true, regMethods, cbClasses);
                if (ctx != null && !ctx.didReg) {}
            }
        }

        out.println();
        out.println("##### REGISTRATION METHODS (" + regMethods.size() + ")");
        int hi = 0;
        for (ClassDef cls : classes.values()) {
            for (Method m : cls.getMethods()) {
                String key = cls.getType() + "->" + m.getName();
                if (!regMethods.contains(key)) continue;
                out.println();
                out.println("===== REGISTRATION " + m.getDefiningClass() + " . " + m.getName()
                    + " params=" + paramsStr(m) + " regs=" + (m.getImplementation() == null ? -1 : m.getImplementation().getRegisterCount()) + " =====");
                hi = dumpMethod(cls, m);
            }
        }

        out.println();
        out.println("##### HOOK CALL SITES (extracted, one per line)");
        for (ClassDef cls : classes.values()) {
            for (Method m : cls.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) continue;
                extractCallSites(cls, m);
            }
        }

        out.println();
        out.println("##### CALLBACK CLASSES (" + cbClasses.size() + ")");
        List<String> cbList = new ArrayList<>(cbClasses);
        Collections.sort(cbList);
        for (String t : cbList) {
            ClassDef cls = classes.get(t);
            if (cls == null) continue;
            out.println();
            out.println("===== CALLBACK " + cls.getType() + " extends " + cls.getSuperclass() + " =====");
            for (Method m : cls.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) continue;
                out.println("--- method " + m.getName() + paramsStr(m) + " ---");
                dumpMethod(cls, m);
            }
        }
        out.flush();
        if (fileOs != System.out) fileOs.close();
    }

    static String findXposedInit(String apk) throws Exception {
        ZipFile zf = new ZipFile(apk);
        ZipEntry init = zf.getEntry("assets/xposed_init");
        if (init != null) {
            byte[] b = ByteStreams.toByteArray(zf.getInputStream(init));
            String s = new String(b, StandardCharsets.UTF_8).trim();
            zf.close();
            return s;
        }
        zf.close();
        return null;
    }

    static String paramsStr(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (MethodParameter p : m.getParameters()) sb.append(p.getType());
        sb.append(")");
        try { sb.append(m.getReturnType()); } catch (Throwable t) {}
        return sb.toString();
    }

    // ---------------- 简单寄存器跟踪 ----------------
    static class RegVal {
        String kind; Object data;
        RegVal(String k, Object d) { kind = k; data = d; }
        public String toString() {
            if (kind.equals("str")) return "str=" + esc((String) data);
            if (kind.equals("cls")) return "cls=" + data;
            if (kind.equals("obj")) return "obj=" + data;
            if (kind.equals("int")) return "int=" + data;
            if (kind.equals("wide")) return "wide=" + data;
            if (kind.equals("pkg")) return "PKG";
            if (kind.equals("arr")) {
                StringBuilder sb = new StringBuilder("arr[");
                Map<Integer, RegVal> mm = (Map<Integer, RegVal>) data;
                for (Map.Entry<Integer, RegVal> e : mm.entrySet()) sb.append(e.getKey()).append("=").append(e.getValue()).append(" ");
                return sb.append("]").toString();
            }
            if (kind.equals("boxed")) return "boxed=" + data;
            if (kind.equals("field")) return "field=" + data;
            return kind;
        }
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
    }

    static class ScanCtx {
        boolean didReg;
    }

    static Map<Integer, RegVal> newRegs(int count) {
        Map<Integer, RegVal> r = new HashMap<>();
        for (int i = 0; i < count; i++) r.put(i, null);
        return r;
    }

    static String rget(Map<Integer, RegVal> regs, int r) {
        RegVal v = regs.get(r);
        return v == null ? "?" : v.toString();
    }

    static String refStr(Reference r) {
        if (r == null) return "";
        return r.toString();
    }

    // 返回 true 表示本方法里出现了 hook 注册 API
    static ScanCtx scanMethod(ClassDef cls, Method m, boolean annotate, Set<String> regMethods, Set<String> cbClasses) {
        MethodImplementation impl = m.getImplementation();
        Map<Integer, RegVal> regs = newRegs(impl.getRegisterCount());
        boolean isReg = false;
        for (Instruction insn : impl.getInstructions()) {
            String op = insn.getOpcode().name();
            try {
                if (insn instanceof ReferenceInstruction && ((ReferenceInstruction) insn).getReference() instanceof MethodReference) {
                    MethodReference mr = (MethodReference) ((ReferenceInstruction) insn).getReference();
                    String n = mr.getName();
                    if (n.equals("findAndHookMethod") || n.equals("findAndHookConstructor") ||
                        n.equals("hookAllMethods") || n.equals("hookAllConstructors") ||
                        n.equals("returnConstant") || n.equals("findClass") || n.equals("findAndHookMethodIfExists")) {
                        isReg = true;
                        if (mr.getDefiningClass().startsWith("Lde/robv/android/xposed/")) {
                            regMethods.add(cls.getType() + "->" + m.getName());
                            // 记录回调类：varargs 里可能有 obj
                            collectCbFromRegs(regs, insn, cbClasses);
                        }
                    }
                }
            } catch (Throwable t) {}
            trackRegs(regs, insn);
        }
        ScanCtx c = new ScanCtx();
        c.didReg = isReg;
        return c;
    }

    static void collectCbFromRegs(Map<Integer, RegVal> regs, Instruction insn, Set<String> cbClasses) {
        try {
            int[] rs = argRegs(insn);
            for (int r : rs) {
                RegVal v = regs.get(r);
                if (v == null) continue;
                if (v.kind.equals("obj") && ((String) v.data).contains("$")) cbClasses.add((String) v.data);
                if (v.kind.equals("arr")) {
                    for (RegVal e : ((Map<Integer, RegVal>) v.data).values()) {
                        if (e != null && e.kind.equals("obj") && ((String) e.data).contains("$")) cbClasses.add((String) e.data);
                    }
                }
            }
        } catch (Throwable t) {}
    }

    static void trackRegs(Map<Integer, RegVal> regs, Instruction insn) {
        String op = insn.getOpcode().name();
        try {
            if (op.equals("CONST_4") || op.equals("CONST_16") || op.equals("CONST") || op.equals("CONST_HIGH16")) {
                NarrowLiteralInstruction n = (NarrowLiteralInstruction) insn;
                regs.put(((OneRegisterInstruction) insn).getRegisterA(), new RegVal("int", n.getNarrowLiteral()));
            } else if (op.startsWith("CONST_WIDE")) {
                WideLiteralInstruction w = (WideLiteralInstruction) insn;
                int r = ((OneRegisterInstruction) insn).getRegisterA();
                regs.put(r, new RegVal("wide", w.getWideLiteral()));
                regs.put(r + 1, new RegVal("wide", w.getWideLiteral()));
            } else if (op.equals("CONST_STRING") || op.equals("CONST_STRING_JUMBO")) {
                StringReference s = (StringReference) ((ReferenceInstruction) insn).getReference();
                regs.put(((OneRegisterInstruction) insn).getRegisterA(), new RegVal("str", s.getString()));
            } else if (op.equals("CONST_CLASS")) {
                Reference ref = ((ReferenceInstruction) insn).getReference();
                regs.put(((OneRegisterInstruction) insn).getRegisterA(), new RegVal("cls", ref.toString()));
            } else if (op.equals("NEW_INSTANCE")) {
                Reference ref = ((ReferenceInstruction) insn).getReference();
                String t = ref instanceof TypeReference ? ((TypeReference) ref).getType() : ref.toString();
                regs.put(((OneRegisterInstruction) insn).getRegisterA(), new RegVal("obj", t));
            } else if (op.equals("SGET_OBJECT") || op.equals("SGET") || op.equals("SGET_BOOLEAN") || op.equals("SGET_BYTE") ||
                       op.equals("SGET_CHAR") || op.equals("SGET_SHORT") || op.equals("SGET_WIDE")) {
                FieldReference f = (FieldReference) ((ReferenceInstruction) insn).getReference();
                TwoRegisterInstruction t = (TwoRegisterInstruction) insn;
                regs.put(t.getRegisterA(), new RegVal("field", f.toString()));
            } else if (op.equals("IGET_OBJECT") || op.equals("IGET") || op.equals("IGET_BOOLEAN") || op.equals("IGET_WIDE")) {
                FieldReference f = (FieldReference) ((ReferenceInstruction) insn).getReference();
                if (f.getDefiningClass().startsWith("Lde/robv/android/xposed/callbacks/XC_LoadPackage$LoadPackageParam;")
                    && f.getName().equals("packageName")) {
                    regs.put(((TwoRegisterInstruction) insn).getRegisterA(), new RegVal("pkg", null));
                }
            } else if (op.startsWith("MOVE_OBJECT") || op.equals("MOVE") || op.startsWith("MOVE_WIDE")) {
                TwoRegisterInstruction t = (TwoRegisterInstruction) insn;
                regs.put(t.getRegisterA(), regs.get(t.getRegisterB()));
                if (op.startsWith("MOVE_WIDE")) regs.put(t.getRegisterA() + 1, regs.get(t.getRegisterB()));
            } else if (op.startsWith("MOVE_RESULT")) {
                OneRegisterInstruction o = (OneRegisterInstruction) insn;
                regs.put(o.getRegisterA(), regs.get(-1));
            } else if (op.equals("FILLED_NEW_ARRAY") || op.equals("FILLED_NEW_ARRAY_RANGE")) {
                Map<Integer, RegVal> mm = new HashMap<>();
                int[] rs = argRegs(insn);
                for (int i = 0; i < rs.length; i++) mm.put(i, regs.get(rs[i]));
                regs.put(-1, new RegVal("arr", mm));
            } else if (op.equals("NEW_ARRAY")) {
                TwoRegisterInstruction t = (TwoRegisterInstruction) insn;
                Map<Integer, RegVal> mm = new HashMap<>();
                regs.put(t.getRegisterA(), new RegVal("arr", mm));
            } else if (op.equals("APUT") || op.equals("APUT_OBJECT") || op.equals("APUT_BOOLEAN") || op.equals("APUT_WIDE")
                       || op.equals("APUT_BYTE") || op.equals("APUT_CHAR") || op.equals("APUT_SHORT")) {
                ThreeRegisterInstruction t = (ThreeRegisterInstruction) insn;
                RegVal arr = regs.get(t.getRegisterB());
                if (arr != null && arr.kind.equals("arr")) {
                    RegVal idx = regs.get(t.getRegisterC());
                    int i = (idx != null && idx.kind.equals("int")) ? (Integer) idx.data : -1;
                    if (i >= 0) ((Map<Integer, RegVal>) arr.data).put(i, regs.get(t.getRegisterA()));
                }
            } else if (op.equals("RETURN") || op.equals("RETURN_OBJECT") || op.equals("RETURN_WIDE")) {
                OneRegisterInstruction o = (OneRegisterInstruction) insn;
                regs.put(-1, regs.get(o.getRegisterA()));
            } else if (op.equals("<init>") || (insn instanceof ReferenceInstruction && ((ReferenceInstruction) insn).getReference() instanceof MethodReference)) {
                // boxed constructor: new Integer(1) 等
                try {
                    MethodReference mr = (MethodReference) ((ReferenceInstruction) insn).getReference();
                    int[] rs = argRegs(insn);
                    if (mr.getName().equals("<init>")) {
                        if (rs.length >= 2) {
                            String dc = mr.getDefiningClass();
                            RegVal a = regs.get(rs[1]);
                            if (dc.equals("Ljava/lang/Integer;") || dc.equals("Ljava/lang/Long;") || dc.equals("Ljava/lang/Boolean;")
                                || dc.equals("Ljava/lang/Short;") || dc.equals("Ljava/lang/Byte;") || dc.equals("Ljava/lang/Character;")) {
                                String v = a == null ? "?" : a.toString();
                                regs.put(rs[0], new RegVal("boxed", dc + "(" + v + ")"));
                            }
                        }
                    } else if (mr.getDefiningClass().equals("Ljava/lang/Class;") && mr.getName().equals("forName")) {
                        RegVal a = rs.length > 0 ? regs.get(rs[0]) : null;
                        if (a != null && a.kind.equals("str")) regs.put(-1, new RegVal("cls", "L" + ((String) a.data).replace('.', '/') + ";"));
                    } else if (mr.getDefiningClass().equals("Lde/robv/android/xposed/XposedHelpers;") && mr.getName().equals("findClass")) {
                        RegVal a = rs.length > 0 ? regs.get(rs[0]) : null;
                        if (a != null && a.kind.equals("str")) regs.put(-1, new RegVal("cls", "L" + ((String) a.data).replace('.', '/') + ";"));
                    }
                } catch (Throwable t) {}
            }
        } catch (Throwable t) {}
    }

    static int[] argRegs(Instruction insn) {
        if (insn instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction f = (FiveRegisterInstruction) insn;
            int n = f.getRegisterCount();
            int[] r = new int[]{f.getRegisterC(), f.getRegisterD(), f.getRegisterE(), f.getRegisterF(), f.getRegisterG()};
            int[] o = new int[n];
            System.arraycopy(r, 0, o, 0, n);
            return o;
        }
        RegisterRangeInstruction rr = (RegisterRangeInstruction) insn;
        int[] o = new int[rr.getRegisterCount()];
        for (int j = 0; j < o.length; j++) o[j] = rr.getStartRegister() + j;
        return o;
    }

    // ---------------- 输出格式化 ----------------
    static int dumpMethod(ClassDef cls, Method m) {
        MethodImplementation impl = m.getImplementation();
        Map<Integer, RegVal> regs = newRegs(impl.getRegisterCount());
        int addr = 0;
        for (Instruction insn : impl.getInstructions()) {
            String line = fmt(insn, addr);
            String ann = setResultAnn(insn, regs);
            if (ann != null) line = line + "    ; " + ann;
            out.println(String.format("%04x  %s", addr, line));
            trackRegs(regs, insn);
            addr += insn.getCodeUnits();
        }
        return addr;
    }

    static String setResultAnn(Instruction insn, Map<Integer, RegVal> regs) {
        try {
            if (!(insn instanceof ReferenceInstruction)) return null;
            Reference r = ((ReferenceInstruction) insn).getReference();
            if (!(r instanceof MethodReference)) return null;
            MethodReference mr = (MethodReference) r;
            boolean isSet = mr.getDefiningClass().startsWith("Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;")
                && (mr.getName().equals("setResult") || mr.getName().equals("setResultWithoutChecking"));
            boolean isReplace = mr.getDefiningClass().equals("Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;")
                && mr.getName().equals("replaceHookedMethod");
            if (!isSet && !isReplace) return null;
            int[] rs = argRegs(insn);
            if (rs.length >= 2) {
                RegVal v = regs.get(rs[1]);
                String label = isReplace ? "return(" : "setResult(";
                return v == null ? label + "?)" : label + v + ")";
            }
        } catch (Throwable t) {}
        return null;
    }

    static String fmt(Instruction insn, int addr) {
        String op = insn.getOpcode().name();
        try {
            if (insn instanceof ArrayPayload || insn instanceof SwitchPayload) return op;
            if (insn instanceof ReferenceInstruction && ((ReferenceInstruction) insn).getReference() instanceof MethodReference) {
                MethodReference mr = (MethodReference) ((ReferenceInstruction) insn).getReference();
                String def = shortType(mr.getDefiningClass());
                StringBuilder sb = new StringBuilder(op);
                sb.append(" {");
                int[] rs = argRegs(insn);
                for (int i = 0; i < rs.length; i++) { if (i > 0) sb.append(", "); sb.append("v").append(rs[i]); }
                sb.append("}, ").append(def).append("->").append(mr.getName()).append(mr.getParameterTypes());
                try { sb.append(mr.getReturnType()); } catch (Throwable t) {}
                return sb.toString();
            }
            if (insn instanceof ReferenceInstruction && ((ReferenceInstruction) insn).getReference() instanceof FieldReference) {
                FieldReference f = (FieldReference) ((ReferenceInstruction) insn).getReference();
                String def = shortType(f.getDefiningClass());
                if (insn instanceof TwoRegisterInstruction) {
                    TwoRegisterInstruction t = (TwoRegisterInstruction) insn;
                    return op + " v" + t.getRegisterA() + ", " + def + "->" + f.getName() + ":" + f.getType();
                }
                return op + " " + def + "->" + f.getName() + ":" + f.getType();
            }
            if (insn instanceof ReferenceInstruction && ((ReferenceInstruction) insn).getReference() instanceof StringReference) {
                StringReference s = (StringReference) ((ReferenceInstruction) insn).getReference();
                return op + " v" + ((OneRegisterInstruction) insn).getRegisterA() + ", \"" + esc(s.getString()) + "\"";
            }
            if (insn instanceof ReferenceInstruction && ((ReferenceInstruction) insn).getReference() instanceof TypeReference) {
                String t = ((TypeReference) ((ReferenceInstruction) insn).getReference()).getType();
                if (insn instanceof TwoRegisterInstruction) {
                    TwoRegisterInstruction tt = (TwoRegisterInstruction) insn;
                    return op + " v" + tt.getRegisterA() + ", " + t;
                }
                return op + " v" + ((OneRegisterInstruction) insn).getRegisterA() + ", " + t;
            }
            if (op.equals("CONST_4") || op.equals("CONST_16") || op.equals("CONST") || op.equals("CONST_HIGH16")) {
                NarrowLiteralInstruction n = (NarrowLiteralInstruction) insn;
                return op + " v" + ((OneRegisterInstruction) insn).getRegisterA() + ", " + n.getNarrowLiteral();
            }
            if (op.startsWith("CONST_WIDE")) {
                WideLiteralInstruction w = (WideLiteralInstruction) insn;
                return op + " v" + ((OneRegisterInstruction) insn).getRegisterA() + ", " + w.getWideLiteral();
            }
            if (insn instanceof OffsetInstruction) {
                OffsetInstruction o = (OffsetInstruction) insn;
                int target = addr + o.getCodeOffset();
                if (insn instanceof TwoRegisterInstruction) {
                    TwoRegisterInstruction t = (TwoRegisterInstruction) insn;
                    return op + " v" + t.getRegisterA() + ", v" + t.getRegisterB() + " -> +" + o.getCodeOffset() + " (" + target + ")";
                }
                if (insn instanceof OneRegisterInstruction) {
                    OneRegisterInstruction t = (OneRegisterInstruction) insn;
                    return op + " v" + t.getRegisterA() + " -> +" + o.getCodeOffset() + " (" + target + ")";
                }
                return op + " -> +" + o.getCodeOffset() + " (" + target + ")";
            }
            if (insn instanceof ThreeRegisterInstruction) {
                ThreeRegisterInstruction t = (ThreeRegisterInstruction) insn;
                return op + " v" + t.getRegisterA() + ", v" + t.getRegisterB() + ", v" + t.getRegisterC();
            }
            if (insn instanceof TwoRegisterInstruction) {
                TwoRegisterInstruction t = (TwoRegisterInstruction) insn;
                return op + " v" + t.getRegisterA() + ", v" + t.getRegisterB();
            }
            if (insn instanceof OneRegisterInstruction) {
                OneRegisterInstruction t = (OneRegisterInstruction) insn;
                return op + " v" + t.getRegisterA();
            }
            return op;
        } catch (Throwable t) {
            return op + " #fmt-err";
        }
    }

    static String shortType(String t) {
        if (t == null) return "";
        if (t.startsWith("L") && t.endsWith(";")) t = t.substring(1, t.length() - 1);
        return t.replace('/', '.');
    }

    static String toDot(String t) {
        return shortType(t);
    }

    // ---------------- hook 调用点提取 ----------------
    static void extractCallSites(ClassDef cls, Method m) {
        MethodImplementation impl = m.getImplementation();
        Map<Integer, RegVal> regs = newRegs(impl.getRegisterCount());
        String curPkg = null;
        for (Instruction insn : impl.getInstructions()) {
            trackRegs(regs, insn);
            String op = insn.getOpcode().name();
            if (!op.startsWith("INVOKE")) continue;
            try {
                Reference ref = ((ReferenceInstruction) insn).getReference();
                if (!(ref instanceof MethodReference)) continue;
                MethodReference mr = (MethodReference) ref;
                String n = mr.getName();
                boolean isHookApi = n.equals("findAndHookMethod") || n.equals("findAndHookConstructor") ||
                    n.equals("hookAllMethods") || n.equals("hookAllConstructors") || n.equals("findAndHookMethodIfExists");
                if (!isHookApi) {
                    if (n.equals("equals") && mr.getDefiningClass().equals("Ljava/lang/String;")) {
                        int[] rs = argRegs(insn);
                        if (rs.length >= 2) {
                            RegVal a = regs.get(rs[0]), b = regs.get(rs[1]);
                            String g = null;
                            if (a != null && a.kind.equals("pkg") && b != null && b.kind.equals("str")) g = (String) b.data;
                            else if (b != null && b.kind.equals("pkg") && a != null && a.kind.equals("str")) g = (String) a.data;
                            if (g != null) curPkg = g;
                        }
                    }
                    continue;
                }
                // 参数槽位：按实际签名推导 className/methodName/varargs
                boolean isCtor = n.equals("findAndHookConstructor") || n.equals("hookAllConstructors");
                List<? extends CharSequence> pts = mr.getParameterTypes();
                int varIdx = pts.size() - 1;
                int methodIdx = -1;
                for (int i = 0; i < varIdx; i++) {
                    if (pts.get(i).equals("Ljava/lang/String;")) methodIdx = i;
                }
                int[] rs = argRegs(insn);
                String className = null, methodName = null, cb = null;
                String classNameHint = null, methodNameHint = null;
                RegVal c0 = rs.length > 0 ? regs.get(rs[0]) : null;
                if (c0 != null && c0.kind.equals("str")) className = (String) c0.data;
                else if (c0 != null && (c0.kind.equals("cls") || c0.kind.equals("obj"))) className = toDot((String) c0.data);
                else if (c0 != null) classNameHint = c0.toString();
                if (!isCtor && methodIdx >= 0 && methodIdx < rs.length) {
                    RegVal cm = regs.get(rs[methodIdx]);
                    if (cm != null && cm.kind.equals("str")) methodName = (String) cm.data;
                    else if (cm != null) methodNameHint = cm.toString();
                } else if (isCtor) {
                    methodName = "<init>";
                }
                RegVal var = varIdx >= 0 && varIdx < rs.length ? regs.get(rs[varIdx]) : null;
                cb = cbFromArr(var);
                if (className == null && classNameHint == null && methodName == null && methodNameHint == null) continue;
                String pkgStr = curPkg == null ? "?" : curPkg;
                String clsStr = className != null ? className : (classNameHint != null ? classNameHint : "?");
                String mthStr = methodName != null ? methodName : (methodNameHint != null ? methodNameHint : "?");
                String cbStr = cb != null ? cb : "?";
                out.println("HOOK " + m.getDefiningClass() + "." + m.getName()
                    + "  api=" + n
                    + "  pkg=" + pkgStr
                    + "  class=" + clsStr
                    + "  method=" + mthStr
                    + "  cb=" + cbStr
                    + "  (type=" + (methodIdx > 0 || isCtor ? "class" : "string") + ")");
            } catch (Throwable t) {}
        }
    }

    static String cbFromArr(RegVal var) {
        if (var == null) return null;
        if (var.kind.equals("obj")) return (String) var.data;
        if (var.kind.equals("arr")) {
            for (RegVal e : ((Map<Integer, RegVal>) var.data).values()) {
                if (e != null && e.kind.equals("obj")) return (String) e.data;
                if (e != null && e.kind.equals("boxed")) return e.toString();
            }
        }
        return null;
    }
}
