package cn.mhook.analyze;

import com.google.common.io.ByteStreams;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.MethodParameter;
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
import org.jf.dexlib2.iface.instruction.formats.ArrayPayload;
import org.jf.dexlib2.iface.instruction.SwitchPayload;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 设备端 XP 模块 hook 提取器（独立于解释器，只依赖 dexlib2 原始指令）。
 * 输出紧凑、AI 可读的文本：注册点清单 + 每个回调的 setResult 值；
 * 无法静态确定值的回调附完整方法体，供 AI 判定。
 */
public class XpExtract {

    static final int MAX_LEN = 400000;

    static List<DexBackedDexFile> dexList = new ArrayList<DexBackedDexFile>();
    static Map<String, ClassDef> classes = new HashMap<String, ClassDef>();
    static StringBuilder sb;

    public static String extract(File apk) {
        sb = new StringBuilder(65536);
        dexList.clear();
        classes.clear();
        usedCb.clear();
        resolvedHookCount = 0;
        totalStr = 0;
        readableStr = 0;
        encStr = 0;
        arabicStr = 0;
        nonAsciiIdent = 0;
        heavyObfuscation = false;
        try {
            loadDex(apk);
            String entry = findXposedInit(apk);
            line("##### META");
            line("apk=" + apk.getName());
            line("xposed_init=" + entry);
            line("class_count=" + classes.size());

            Set<String> regMethods = new LinkedHashSet<String>();
            Set<String> cbClasses = new LinkedHashSet<String>();
            for (ClassDef cls : classes.values()) {
                String ctype = cls.getType();
                if (hasNonAscii(ctype)) nonAsciiIdent++;
                for (Method m : cls.getMethods()) {
                    if (hasNonAscii(m.getName())) nonAsciiIdent++;
                    if (m.getImplementation() == null) continue;
                    scanRegs(cls, m, regMethods, cbClasses);
                }
            }
            // 兜底：所有 XC_MethodHook/XC_MethodReplacement 子类都算回调
            for (ClassDef cls : classes.values()) {
                String sup = cls.getSuperclass();
                if (sup != null && (sup.equals("Lde/robv/android/xposed/XC_MethodHook;")
                        || sup.equals("Lde/robv/android/xposed/XC_MethodReplacement;"))) {
                    cbClasses.add(cls.getType());
                }
            }

            line("");
            line("##### HOOK SITES");
            hookCount = 0;
            for (ClassDef cls : classes.values()) {
                for (Method m : cls.getMethods()) {
                    if (m.getImplementation() == null) continue;
                    extractCallSites(cls, m);
                }
            }
            line("##### (total " + hookCount + " hook sites)");

            line("");
            line("##### OBFUSCATION ASSESSMENT");
            StringBuilder adv = new StringBuilder();
            boolean heavy = false;
            if (hookCount > 0 && resolvedHookCount == 0) {
                heavy = true;
                adv.append("hook 目标全部无法解析（" + hookCount + " 个注册点均为空）");
            } else if (hookCount > 0 && resolvedHookCount * 2 < hookCount) {
                heavy = true;
                adv.append("部分 hook 目标无法解析（" + resolvedHookCount + "/" + hookCount + "）");
            }
            if (totalStr > 10 && encStr > totalStr / 2) {
                heavy = true;
                if (adv.length() > 0) adv.append("；");
                adv.append("字符串疑似加密（可读 " + readableStr + "/" + totalStr + "）");
            }
            if (nonAsciiIdent > 0) {
                if (adv.length() > 0) adv.append("；");
                adv.append("存在 " + nonAsciiIdent + " 处非常规字符标识符（混淆特征）");
            }
            line("hook_sites=" + hookCount + " resolved=" + resolvedHookCount
                    + " strings=" + totalStr + "(readable " + readableStr + "/enc " + encStr + "/arabic " + arabicStr + ")"
                    + " nonAsciiIdent=" + nonAsciiIdent);
            if (heavy) {
                heavyObfuscation = true;
                line("=> 检测到高强度混淆：" + adv + "。静态分析无法还原 hook 目标，建议改用运行时动态抓取。");
            } else {
                line("=> 未检测到明显混淆。");
            }

            line("");
            line("##### CALLBACK RESULTS (" + usedCb.size() + ")");
            List<String> cbList = new ArrayList<String>(usedCb);
            Collections.sort(cbList);
            for (String t : cbList) {
                if (sb.length() > MAX_LEN) {
                    line("### 输出超长截断，剩余回调省略");
                    break;
                }
                dumpCallback(t);
            }
        } catch (Throwable t) {
            line("### extract error: " + t);
        }
        if (sb.length() > MAX_LEN) sb.setLength(MAX_LEN);
        return sb.toString();
    }

    static int hookCount = 0;
    static Set<String> usedCb = new LinkedHashSet<String>();
    static int resolvedHookCount = 0;
    static int totalStr = 0, readableStr = 0, encStr = 0, arabicStr = 0;
    static int nonAsciiIdent = 0;
    public static boolean heavyObfuscation = false;

    static void loadDex(File apk) throws Exception {
        ZipFile zf = new ZipFile(apk);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.getName().endsWith(".dex")) continue;
                try {
                    DexBackedDexFile dex = new DexBackedDexFile(Opcodes.forApi(28),
                            ByteStreams.toByteArray(zf.getInputStream(e)));
                    dexList.add(dex);
                    for (ClassDef cls : dex.getClasses()) classes.put(cls.getType(), cls);
                } catch (Throwable t) {
                    line("# skip dex " + e.getName() + ": " + t);
                }
            }
        } finally {
            zf.close();
        }
    }

    static String findXposedInit(File apk) throws Exception {
        ZipFile zf = new ZipFile(apk);
        try {
            ZipEntry init = zf.getEntry("assets/xposed_init");
            if (init != null) {
                byte[] b = ByteStreams.toByteArray(zf.getInputStream(init));
                return new String(b, StandardCharsets.UTF_8).trim();
            }
        } finally {
            zf.close();
        }
        return null;
    }

    static void line(String s) {
        sb.append(s).append('\n');
    }

    // ---------------- 寄存器跟踪 ----------------
    static class RegVal {
        String kind;
        Object data;
        RegVal(String k, Object d) { kind = k; data = d; }
        public String toString() {
            if (kind.equals("str")) return "str=" + esc((String) data);
            if (kind.equals("cls")) return "cls=" + data;
            if (kind.equals("obj")) return "obj=" + data;
            if (kind.equals("int")) return "int=" + data;
            if (kind.equals("wide")) return "wide=" + data;
            if (kind.equals("pkg")) return "PKG";
            if (kind.equals("boxed")) return "boxed=" + data;
            if (kind.equals("field")) return "field=" + data;
            if (kind.equals("arr")) {
                StringBuilder t = new StringBuilder("arr[");
                Map<Integer, RegVal> mm = (Map<Integer, RegVal>) data;
                for (Map.Entry<Integer, RegVal> e : mm.entrySet()) t.append(e.getKey()).append("=").append(e.getValue()).append(" ");
                return t.append("]").toString();
            }
            return kind;
        }
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
    }

    static boolean hasNonAscii(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) return true;
        }
        return false;
    }

    static boolean hasArabic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) return true;
        }
        return false;
    }

    static void countStr(String s) {
        totalStr++;
        if (s == null) return;
        if (hasArabic(s)) {
            arabicStr++;
            return;
        }
        // base62 随机串特征：含大小写字母+数字、无标点、长度>=8
        int upper = 0, lower = 0, digit = 0, punct = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') upper++;
            else if (c >= 'a' && c <= 'z') lower++;
            else if (c >= '0' && c <= '9') digit++;
            else if (c > 0x7F) { punct = 99; break; }
            else punct++;
        }
        if (s.length() >= 8 && punct == 0 && upper > 0 && lower > 0 && digit > 0) encStr++;
        else readableStr++;
    }

    static Map<Integer, RegVal> newRegs(int count) {
        Map<Integer, RegVal> r = new HashMap<Integer, RegVal>();
        for (int i = 0; i < count; i++) r.put(i, null);
        return r;
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
                countStr(s.getString());
                regs.put(((OneRegisterInstruction) insn).getRegisterA(), new RegVal("str", s.getString()));
            } else if (op.equals("CONST_CLASS")) {
                Reference ref = ((ReferenceInstruction) insn).getReference();
                regs.put(((OneRegisterInstruction) insn).getRegisterA(), new RegVal("cls", ref.toString()));
            } else if (op.equals("NEW_INSTANCE")) {
                Reference ref = ((ReferenceInstruction) insn).getReference();
                String t = ref instanceof TypeReference ? ((TypeReference) ref).getType() : ref.toString();
                regs.put(((OneRegisterInstruction) insn).getRegisterA(), new RegVal("obj", t));
            } else if (op.startsWith("SGET")) {
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
                Map<Integer, RegVal> mm = new HashMap<Integer, RegVal>();
                int[] rs = argRegs(insn);
                for (int i = 0; i < rs.length; i++) mm.put(i, regs.get(rs[i]));
                regs.put(-1, new RegVal("arr", mm));
            } else if (op.equals("NEW_ARRAY")) {
                TwoRegisterInstruction t = (TwoRegisterInstruction) insn;
                regs.put(t.getRegisterA(), new RegVal("arr", new HashMap<Integer, RegVal>()));
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
            } else if (insn instanceof ReferenceInstruction
                    && ((ReferenceInstruction) insn).getReference() instanceof MethodReference) {
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
            }
        } catch (Throwable t) {
        }
    }

    // ---------------- 注册点扫描 ----------------
    static void scanRegs(ClassDef cls, Method m, Set<String> regMethods, Set<String> cbClasses) {
        MethodImplementation impl = m.getImplementation();
        Map<Integer, RegVal> regs = newRegs(impl.getRegisterCount());
        for (Instruction insn : impl.getInstructions()) {
            try {
                if (insn instanceof ReferenceInstruction
                        && ((ReferenceInstruction) insn).getReference() instanceof MethodReference) {
                    MethodReference mr = (MethodReference) ((ReferenceInstruction) insn).getReference();
                    String n = mr.getName();
                    if ((n.equals("findAndHookMethod") || n.equals("findAndHookConstructor")
                            || n.equals("hookAllMethods") || n.equals("hookAllConstructors")
                            || n.equals("findAndHookMethodIfExists")) && mr.getDefiningClass().startsWith("Lde/robv/android/xposed/")) {
                        regMethods.add(cls.getType() + "->" + m.getName());
                        collectCbFromRegs(regs, insn, cbClasses);
                    }
                }
            } catch (Throwable t) {
            }
            trackRegs(regs, insn);
        }
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
        } catch (Throwable t) {
        }
    }

    // ---------------- hook 调用点输出 ----------------
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
                boolean isHookApi = n.equals("findAndHookMethod") || n.equals("findAndHookConstructor")
                        || n.equals("hookAllMethods") || n.equals("hookAllConstructors") || n.equals("findAndHookMethodIfExists");
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
                if (cb != null) usedCb.add(cb);
                String clsStr = className != null ? className : (classNameHint != null ? classNameHint : "?");
                String mthStr = methodName != null ? methodName : (methodNameHint != null ? methodNameHint : "?");
                String cbStr = cb != null ? cb : "?";
                if (!"?".equals(clsStr) && !"?".equals(mthStr) && !"?".equals(cbStr)) resolvedHookCount++;
                line("HOOK api=" + n
                        + " pkg=" + (curPkg == null ? "?" : curPkg)
                        + " class=" + clsStr
                        + " method=" + mthStr
                        + " cb=" + cbStr);
                hookCount++;
            } catch (Throwable t) {
            }
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

    // ---------------- 回调输出 ----------------
    static void dumpCallback(String t) {
        ClassDef cls = classes.get(t);
        if (cls == null) return;
        line("### CALLBACK " + t);
        for (Method m : cls.getMethods()) {
            MethodImplementation impl = m.getImplementation();
            if (impl == null) continue;
            Map<Integer, RegVal> regs = newRegs(impl.getRegisterCount());
            List<String> srs = new ArrayList<String>();
            boolean ambiguous = false;
            int addr = 0;
            for (Instruction insn : impl.getInstructions()) {
                String ann = setResultAnn(insn, regs);
                if (ann != null) {
                    srs.add(ann);
                    if (ann.contains("?)")) ambiguous = true;
                }
                trackRegs(regs, insn);
                addr += insn.getCodeUnits();
            }
            if (srs.isEmpty()) continue;
            for (String a : srs) line("  " + m.getName() + " => " + a);
            if (ambiguous) {
                line("  -- body " + m.getName() + " --");
                Map<Integer, RegVal> r2 = newRegs(impl.getRegisterCount());
                int a2 = 0;
                for (Instruction insn : impl.getInstructions()) {
                    String s = fmt(insn, a2);
                    String ann = setResultAnn(insn, r2);
                    if (ann != null) s = s + "    ; " + ann;
                    line(String.format("%04x  %s", a2, s));
                    trackRegs(r2, insn);
                    a2 += insn.getCodeUnits();
                }
            }
        }
    }

    static String setResultAnn(Instruction insn, Map<Integer, RegVal> regs) {
        try {
            if (!(insn instanceof ReferenceInstruction)) return null;
            Reference r = ((ReferenceInstruction) insn).getReference();
            if (!(r instanceof MethodReference)) return null;
            MethodReference mr = (MethodReference) r;
            boolean isSet = mr.getDefiningClass().startsWith("Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;")
                    && (mr.getName().equals("setResult") || mr.getName().equals("setResultWithoutChecking"));
            boolean isReplace = mr.getDefiningClass().startsWith("Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;")
                    && mr.getName().equals("replaceHookedMethod");
            if (!isSet && !isReplace) return null;
            int[] rs = argRegs(insn);
            if (rs.length >= 2) {
                RegVal v = regs.get(rs[1]);
                String label = isReplace ? "return(" : "setResult(";
                return v == null ? label + "?)" : label + v + ")";
            }
        } catch (Throwable t) {
        }
        return null;
    }

    static String fmt(Instruction insn, int addr) {
        String op = insn.getOpcode().name();
        try {
            if (insn instanceof ArrayPayload || insn instanceof SwitchPayload) return op;
            if (insn instanceof ReferenceInstruction
                    && ((ReferenceInstruction) insn).getReference() instanceof MethodReference) {
                MethodReference mr = (MethodReference) ((ReferenceInstruction) insn).getReference();
                String def = shortType(mr.getDefiningClass());
                StringBuilder sb = new StringBuilder(op).append(" {");
                int[] rs = argRegs(insn);
                for (int i = 0; i < rs.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("v").append(rs[i]);
                }
                sb.append("}, ").append(def).append("->").append(mr.getName()).append(mr.getParameterTypes());
                try { sb.append(mr.getReturnType()); } catch (Throwable t) {}
                return sb.toString();
            }
            if (insn instanceof ReferenceInstruction
                    && ((ReferenceInstruction) insn).getReference() instanceof FieldReference) {
                FieldReference f = (FieldReference) ((ReferenceInstruction) insn).getReference();
                String def = shortType(f.getDefiningClass());
                if (insn instanceof TwoRegisterInstruction) {
                    TwoRegisterInstruction t = (TwoRegisterInstruction) insn;
                    return op + " v" + t.getRegisterA() + ", " + def + "->" + f.getName() + ":" + f.getType();
                }
                return op + " " + def + "->" + f.getName() + ":" + f.getType();
            }
            if (insn instanceof ReferenceInstruction
                    && ((ReferenceInstruction) insn).getReference() instanceof StringReference) {
                StringReference s = (StringReference) ((ReferenceInstruction) insn).getReference();
                return op + " v" + ((OneRegisterInstruction) insn).getRegisterA() + ", \"" + esc(s.getString()) + "\"";
            }
            if (insn instanceof ReferenceInstruction
                    && ((ReferenceInstruction) insn).getReference() instanceof TypeReference) {
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
}
