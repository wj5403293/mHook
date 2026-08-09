package cn.mhook.analyze;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.instruction.formats.*;
import org.jf.dexlib2.iface.instruction.SwitchElement;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * 通用 XP 模块静态分析器。
 * 不依赖任何模块专属约定：通过通用字节码解释器求解常量（含加密字符串表、
 * XOR/base64 等解码器），捕获 findAndHookMethod / hookAllMethods 等挂钩点与
 * setResult / returnConstant 返回值，并结合 LoadPackageParam.packageName 的
 * equals 判断推导目标应用包名。输出可直接导入 mHook 的配置。
 */
public class DexAnalyzer {

    public static class HookPoint {
        public String className;
        public String methodName;
        public String resultData;
        public String returnType;
        public String source;
    }

    public static class AppConfig {
        public String packageName = "";
        public String appName = "";
        public String description = "";
        public final List<HookPoint> hooks = new ArrayList<>();
    }

    public static class Result {
        public final List<AppConfig> apps = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
    }

    // ---------------- 值模型 ----------------
    static final class Val {
        String kind; // int,long,str,cls,class,bytes,array,boxed,obj,constret,null,unknown,pkgname
        Object data;
        Val(String k, Object d) { kind = k; data = d; }
        static Val INT(long v) { return new Val("int", (int) v); }
        static Val LONG(long v) { return new Val("long", v); }
        static Val STR(String s) { return new Val("str", s); }
        static Val CLS(String t) { return new Val("cls", t); }
        static Val CLASS(String t) { return new Val("class", t); }
        static Val SYMC() { return new Val("symc", null); }
        static Val UNKNOWN() { return new Val("unknown", null); }
        static Val NONE() { return new Val("null", null); }
    }

    static final class HookReg {
        String className;
        String methodName;
        String anonClass;
        Val constret;
        String pkg;
        String sourceClass;
        String sourceMethod;
        Val resultVal;
        boolean hasResult;
    }

    static final class State {
        List<DexBackedDexFile> dex = new ArrayList<>();
        Map<String, ClassDef> classes = new HashMap<>();
        Map<String, List<Method>> methodIndex = new HashMap<>();
        Map<String, Val> fields = new HashMap<>();
        Map<String, Val> interpretMemo = new HashMap<>();
        Map<String, List<Val>> classSetResults = new HashMap<>();
        Map<String, Set<String>> classPkgChecks = new HashMap<>();
        Map<String, Set<String>> classEdges = new HashMap<>();
        List<HookReg> regs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
    }

    static final class Sim {
        Val[] regs;
        List<ArrayPayload> arr = new ArrayList<>();
        List<SwitchPayload> sw = new ArrayList<>();
        int pArr = 0, pSw = 0;
        State st;
        int steps = 0;
        Val pending;
        Sim(State st, int regCount) { this.st = st; regs = new Val[regCount]; }        Val get(int r) { return (r >= 0 && r < regs.length) ? regs[r] : null; }
        void set(int r, Val v) { if (r >= 0 && r < regs.length) regs[r] = v; }
        void setPending(Val v) { pending = v; }
        Val takePending() { Val v = pending; pending = null; return v; }
    }

    // ---------------- 对外入口 ----------------
    public static Result analyzeApk(File apk) {
        Result res = new Result();
        State st = new State();
        try {
            loadDex(st, apk);
            if (st.dex.isEmpty()) { res.warnings.add("未找到 dex 文件"); return res; }
            indexClasses(st);
            buildFieldTable(st);
            scanAll(st);
            attachResults(st);
            assignPkgs(st);
            assemble(st, res);
        } catch (Throwable t) {
            t.printStackTrace();
            res.warnings.add("分析异常: " + t);
        }
        return res;
    }

    static void loadDex(State st, File apk) throws Exception {
        try (ZipFile zf = new ZipFile(apk)) {
            List<String> dexNames = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("classes") && n.endsWith(".dex")) dexNames.add(n);
            }
            Collections.sort(dexNames);
            for (String n : dexNames) {
                try (InputStream in = zf.getInputStream(zf.getEntry(n))) {
                    byte[] data = readAll(in);
                    try {
                        st.dex.add(new DexBackedDexFile(Opcodes.forApi(28), data));
                    } catch (Throwable t) {
                        try {
                            st.dex.add(new DexBackedDexFile(Opcodes.getDefault(), data));
                        } catch (Throwable t2) {
                            st.warnings.add("跳过 dex " + n + ": " + t2);
                        }
                    }
                }
            }
        }
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    static void indexClasses(State st) {
        for (DexBackedDexFile d : st.dex) {
            for (ClassDef cls : d.getClasses()) {
                st.classes.put(cls.getType(), cls);
                for (Method m : cls.getMethods()) {
                    String key = cls.getType() + "->" + m.getName();
                    st.methodIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
                }
            }
        }
    }

    // ---------------- 字段表：解释所有 <clinit> ----------------
    static void buildFieldTable(State st) {
        long fp = -1;
        for (int pass = 0; pass < 8; pass++) {
            for (ClassDef cls : st.classes.values()) {
                for (Method m : cls.getMethods()) {
                    if (m.getName().equals("<clinit>")) {
                        interpret(st, m, new ArrayList<Val>(), 0);
                    }
                }
            }
            long nfp = fieldFingerprint(st.fields);
            if (nfp == fp) break;
            fp = nfp;
        }
    }

    static long fieldFingerprint(Map<String, Val> fields) {
        long h = 0;
        for (Map.Entry<String, Val> e : fields.entrySet()) {
            h = h * 31 + e.getKey().hashCode();
            Val v = e.getValue();
            long vh = 0;
            if (v != null) {
                vh = v.kind.hashCode() * 131;
                if (v.data != null) {
                    if (v.kind.equals("bytes")) vh += java.util.Arrays.hashCode((byte[]) v.data);
                    else if (v.kind.equals("array")) vh += ((List<?>) v.data).size();
                    else vh += v.data.toString().hashCode();
                }
            }
            h = h * 31 + vh;
        }
        return h;
    }

    // ---------------- 通用字节码解释器 ----------------
    static final int MAX_STEPS = 200000;
    static final int MAX_DEPTH = 6;

    static boolean isStatic(Method m) {
        try {
            return (m.getAccessFlags() & AccessFlags.STATIC.getValue()) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    static Val interpret(State st, Method m, List<Val> args, int depth) {
        if (depth > MAX_DEPTH) return null;
        MethodImplementation impl = m.getImplementation();
        if (impl == null) return null;
        int regCount = impl.getRegisterCount();
        Sim sim = new Sim(st, regCount);
        collectPayloads(impl, sim);

        List<MethodParameter> params = new ArrayList<>(m.getParameters());
        int p0 = regCount - countParamRegs(params);
        int ri = p0;
        for (int i = 0; i < params.size(); i++) {
            String t = params.get(i).getType();
            Val a = i < args.size() ? args.get(i) : null;
            if (a == null) a = Val.UNKNOWN();
            sim.set(ri, a);
            if (t.equals("J") || t.equals("D")) sim.set(ri + 1, a);
            ri += (t.equals("J") || t.equals("D")) ? 2 : 1;
        }

        String memoKey = null;
        if (depth > 0 && isStatic(m)) {
            StringBuilder sb = new StringBuilder(m.getDefiningClass()).append("->").append(m.getName());
            for (Val a : args) sb.append('|').append(describeVal(a));
            memoKey = sb.toString();
            Val cached = st.interpretMemo.get(memoKey);
            if (cached != null) return cached;
        }

        List<Instruction> insns = new ArrayList<>();
        for (Instruction insn : impl.getInstructions()) insns.add(insn);
        Map<Integer, Integer> addrToIdx = new HashMap<>();
        int addr = 0;
        for (int i = 0; i < insns.size(); i++) {
            addrToIdx.put(addr, i);
            addr += insns.get(i).getCodeUnits();
        }

        int pc = 0;
        while (pc < insns.size()) {
            if (++sim.steps > MAX_STEPS) return null;
            Instruction insn = insns.get(pc);
            Opcode op = insn.getOpcode();
            int thisAddr = 0;
            for (int i = 0; i < pc; i++) thisAddr += insns.get(i).getCodeUnits();

            if (pureOp(sim, insn)) { pc++; continue; }

            if (op == Opcode.RETURN_VOID) return null;
            if (op == Opcode.RETURN || op == Opcode.RETURN_WIDE || op == Opcode.RETURN_OBJECT) {
                Val v = sim.get(((Instruction11x) insn).getRegisterA());
                if (op == Opcode.RETURN && v != null && v.kind.equals("long")) v = Val.INT(((Long) v.data).intValue());
                if (memoKey != null) st.interpretMemo.put(memoKey, v);
                return v;
            }
            if (op == Opcode.GOTO || op == Opcode.GOTO_16 || op == Opcode.GOTO_32) {
                int off = codeOffset(insn);
                Integer t = addrToIdx.get(thisAddr + off);
                if (t == null) return null;
                pc = t; continue;
            }
            if (op == Opcode.IF_EQ || op == Opcode.IF_NE || op == Opcode.IF_LT || op == Opcode.IF_GE ||
                op == Opcode.IF_GT || op == Opcode.IF_LE) {
                Instruction22t i = (Instruction22t) insn;
                Val a = sim.get(i.getRegisterA()), b = sim.get(i.getRegisterB());
                if ((op == Opcode.IF_EQ || op == Opcode.IF_NE) && (isClassLike(a) || isClassLike(b))) {
                    boolean eq = classEq(a, b);
                    boolean cond = (op == Opcode.IF_EQ) ? eq : !eq;
                    if (cond) { Integer t = addrToIdx.get(thisAddr + i.getCodeOffset()); if (t == null) return null; pc = t; }
                    else pc++;
                    continue;
                }
                if (!knownInt(a) || !knownInt(b)) return null;
                boolean cond = cmp(op, intVal(a), intVal(b));
                if (cond) { Integer t = addrToIdx.get(thisAddr + i.getCodeOffset()); if (t == null) return null; pc = t; }
                else pc++;
                continue;
            }
            if (op == Opcode.IF_EQZ || op == Opcode.IF_NEZ || op == Opcode.IF_LTZ || op == Opcode.IF_GEZ ||
                op == Opcode.IF_GTZ || op == Opcode.IF_LEZ) {
                Instruction21t i = (Instruction21t) insn;
                Val a = sim.get(i.getRegisterA());
                if (!knownInt(a)) return null;
                boolean cond = cmp0(op, intVal(a));
                if (cond) { Integer t = addrToIdx.get(thisAddr + i.getCodeOffset()); if (t == null) return null; pc = t; }
                else pc++;
                continue;
            }
            if (op == Opcode.PACKED_SWITCH || op == Opcode.SPARSE_SWITCH) {
                int a = ((OneRegisterInstruction) insn).getRegisterA();
                Val v = sim.get(a);
                if (!knownInt(v)) return null;
                SwitchPayload sp = sim.pSw < sim.sw.size() ? sim.sw.get(sim.pSw++) : null;
                if (sp == null) return null;
                List<? extends SwitchElement> elements = sp.getSwitchElements();
                int target = -1;
                int key = intVal(v);
                for (SwitchElement e : elements) {
                    if (e.getKey() == key) { target = e.getOffset(); break; }
                }
                if (target < 0) { pc++; continue; }
                Integer t = addrToIdx.get(thisAddr + target);
                if (t == null) return null;
                pc = t; continue;
            }
            if (op == Opcode.THROW) return null;

            if (insn instanceof Instruction35c || insn instanceof Instruction3rc) {
                int[] rargs = argRegs(insn);
                Reference ref = refOf(insn);
                invoke(st, sim, rargs, ref, depth, memoKey);
                pc++;
                continue;
            }
            return null;
        }
        return null;
    }

    static void collectPayloads(MethodImplementation impl, Sim sim) {
        for (Instruction insn : impl.getInstructions()) {
            if (insn instanceof ArrayPayload) sim.arr.add((ArrayPayload) insn);
            else if (insn instanceof SwitchPayload) sim.sw.add((SwitchPayload) insn);
        }
    }

    static boolean knownInt(Val v) {
        return v != null && (v.kind.equals("int") || v.kind.equals("long")) && v.data != null;
    }

    static boolean isClassLike(Val v) {
        return v != null && (v.kind.equals("class") || v.kind.equals("symc"));
    }

    static boolean classEq(Val a, Val b) {
        if (a == null || b == null) return false;
        if (a.kind.equals("symc")) return !isVoidOrUnit(b);
        if (b.kind.equals("symc")) return !isVoidOrUnit(a);
        if (a.kind.equals("class") && b.kind.equals("class")) return a.data != null && a.data.equals(b.data);
        return false;
    }

    static boolean isVoidOrUnit(Val v) {
        if (v == null || v.data == null) return true;
        String t = (String) v.data;
        return t.equals("V") || t.equals("Lkotlin/Unit;");
    }

    static String primitiveTypeField(String sgetKey) {
        int sep = sgetKey.indexOf("->");
        if (sep < 0) return null;
        String cls = sgetKey.substring(0, sep), f = sgetKey.substring(sep + 2);
        if (!f.equals("TYPE")) return null;
        if (cls.equals("Ljava/lang/Boolean;")) return "Z";
        if (cls.equals("Ljava/lang/Integer;")) return "I";
        if (cls.equals("Ljava/lang/Long;")) return "J";
        if (cls.equals("Ljava/lang/Short;")) return "S";
        if (cls.equals("Ljava/lang/Byte;")) return "B";
        if (cls.equals("Ljava/lang/Character;")) return "C";
        if (cls.equals("Ljava/lang/Float;")) return "F";
        if (cls.equals("Ljava/lang/Double;")) return "D";
        if (cls.equals("Ljava/lang/Void;")) return "V";
        return null;
    }

    static int intVal(Val v) {
        if (v == null || v.data == null) return 0;
        if (v.kind.equals("int")) return ((Integer) v.data).intValue();
        if (v.kind.equals("long")) return ((Long) v.data).intValue();
        return 0;
    }

    static boolean cmp(Opcode op, int a, int b) {
        switch (op) {
            case IF_EQ: return a == b;
            case IF_NE: return a != b;
            case IF_LT: return a < b;
            case IF_GE: return a >= b;
            case IF_GT: return a > b;
            case IF_LE: return a <= b;
            default: return false;
        }
    }

    static boolean cmp0(Opcode op, int a) {
        switch (op) {
            case IF_EQZ: return a == 0;
            case IF_NEZ: return a != 0;
            case IF_LTZ: return a < 0;
            case IF_GEZ: return a >= 0;
            case IF_GTZ: return a > 0;
            case IF_LEZ: return a <= 0;
            default: return false;
        }
    }

    static int codeOffset(Instruction insn) {
        if (insn instanceof Instruction10t) return ((Instruction10t) insn).getCodeOffset();
        if (insn instanceof Instruction20t) return ((Instruction20t) insn).getCodeOffset();
        return ((Instruction30t) insn).getCodeOffset();
    }

    static int[] argRegs(Instruction insn) {
        if (insn instanceof Instruction35c) {
            Instruction35c i = (Instruction35c) insn;
            int n = i.getRegisterCount();
            int[] r = new int[] { i.getRegisterC(), i.getRegisterD(), i.getRegisterE(), i.getRegisterF(), i.getRegisterG() };
            int[] out = new int[n];
            System.arraycopy(r, 0, out, 0, n);
            return out;
        }
        Instruction3rc i = (Instruction3rc) insn;
        int[] out = new int[i.getRegisterCount()];
        for (int j = 0; j < out.length; j++) out[j] = i.getStartRegister() + j;
        return out;
    }

    static Reference refOf(Instruction insn) {
        if (insn instanceof Instruction35c) return ((Instruction35c) insn).getReference();
        return ((Instruction3rc) insn).getReference();
    }

    static int countParamRegs(List<MethodParameter> params) {
        int n = 0;
        for (MethodParameter p : params) {
            String t = p.getType();
            n += (t.equals("J") || t.equals("D")) ? 2 : 1;
        }
        return n;
    }

    // 解释器中的 invoke：纯函数求值
    static void invoke(State st, Sim sim, int[] args, Reference ref, int depth, String memoKey) {
        if (!(ref instanceof MethodReference)) { sim.setPending(null); return; }
        MethodReference mr = (MethodReference) ref;
        String cls = mr.getDefiningClass();
        String name = mr.getName();
        Val r = null;

        if (cls.equals("Ljava/lang/String;") && name.equals("<init>")) {
            if (args.length >= 2) {
                Val b = sim.get(args[1]);
                if (b != null && b.kind.equals("bytes")) r = Val.STR(new String((byte[]) b.data, StandardCharsets.UTF_8));
                else if (b != null && b.kind.equals("array")) r = Val.STR(intsToUtf8(b));
                else r = Val.STR("");
            } else {
                r = Val.STR("");
            }
            if (args.length >= 1) sim.set(args[0], r);
        } else if (cls.equals("Ljava/lang/Integer;") && name.equals("<init>")) {
            Val x = args.length > 1 ? sim.get(args[1]) : null;
            r = new Val("boxed", new Object[] { "I", knownInt(x) ? (Object) Integer.valueOf(intVal(x)) : null });
            if (args.length >= 1) sim.set(args[0], r);
        } else if (cls.equals("Ljava/lang/Long;") && name.equals("<init>")) {
            Val x = args.length > 1 ? sim.get(args[1]) : null;
            r = new Val("boxed", new Object[] { "J", knownInt(x) ? (Object) Long.valueOf(intVal(x)) : null });
            if (args.length >= 1) sim.set(args[0], r);
        } else if (cls.equals("Ljava/lang/Boolean;") && name.equals("<init>")) {
            Val x = args.length > 1 ? sim.get(args[1]) : null;
            r = new Val("boxed", new Object[] { "Z", knownInt(x) ? (Object) (intVal(x) != 0) : null });
            if (args.length >= 1) sim.set(args[0], r);
        } else if (cls.equals("Ljava/lang/StringBuilder;") && name.equals("append")) {
            Val b = sim.get(args[0]);
            if (b != null && b.kind.equals("sb")) {
                Val x = args.length > 1 ? sim.get(args[1]) : null;
                ((StringBuilder) b.data).append(strOfVal(x));
                r = b;
            }
        } else if (cls.equals("Ljava/lang/StringBuilder;") && name.equals("toString")) {
            Val b = sim.get(args[0]);
            if (b != null && b.kind.equals("sb")) r = Val.STR(b.data.toString());
        } else if (cls.equals("Ljava/lang/String;") && name.equals("valueOf")) {
            Val x = args.length > 0 ? sim.get(args[0]) : null;
            r = Val.STR(strOfVal(x));
        } else if (cls.equals("Ljava/lang/Integer;") && name.equals("valueOf")) {
            Val x = args.length > 0 ? sim.get(args[0]) : null;
            r = new Val("boxed", new Object[] { "I", knownInt(x) ? (Object) Integer.valueOf(intVal(x)) : null });
        } else if (cls.equals("Ljava/lang/Long;") && name.equals("valueOf")) {
            Val x = args.length > 0 ? sim.get(args[0]) : null;
            r = new Val("boxed", new Object[] { "J", knownInt(x) ? (Object) Long.valueOf(intVal(x)) : null });
        } else if (cls.equals("Ljava/lang/Boolean;") && name.equals("valueOf")) {
            Val x = args.length > 0 ? sim.get(args[0]) : null;
            r = new Val("boxed", new Object[] { "Z", knownInt(x) ? (Object) (intVal(x) != 0) : null });
        } else if (cls.equals("Ljava/lang/Integer;") && name.equals("parseInt")) {
            Val x = args.length > 0 ? sim.get(args[0]) : null;
            if (x != null && x.kind.equals("str")) { try { r = Val.INT(Integer.parseInt((String) x.data)); } catch (Throwable ignored) {} }
        } else if (cls.equals("Ljava/lang/Long;") && name.equals("parseLong")) {
            Val x = args.length > 0 ? sim.get(args[0]) : null;
            if (x != null && x.kind.equals("str")) { try { r = Val.LONG(Long.parseLong((String) x.data)); } catch (Throwable ignored) {} }
        } else if (cls.equals("Ljava/lang/Math;")) {
            Val a = args.length > 0 ? sim.get(args[0]) : null;
            Val b = args.length > 1 ? sim.get(args[1]) : null;
            if (name.equals("abs") && knownInt(a)) r = Val.INT(Math.abs(intVal(a)));
            else if (name.equals("max") && knownInt(a) && knownInt(b)) r = Val.INT(Math.max(intVal(a), intVal(b)));
            else if (name.equals("min") && knownInt(a) && knownInt(b)) r = Val.INT(Math.min(intVal(a), intVal(b)));
        } else if (cls.equals("Ljava/lang/String;")) {
            Val a = args.length > 0 ? sim.get(args[0]) : null;
            String s = (a != null && a.kind.equals("str")) ? (String) a.data : null;
            if (s == null) { r = null; }
            else if (name.equals("length")) r = Val.INT(s.length());
            else if (name.equals("trim")) r = Val.STR(s.trim());
            else if (name.equals("toUpperCase")) r = Val.STR(s.toUpperCase());
            else if (name.equals("toLowerCase")) r = Val.STR(s.toLowerCase());
            else if (name.equals("charAt")) { Val i = args.length > 1 ? sim.get(args[1]) : null; if (knownInt(i) && intVal(i) >= 0 && intVal(i) < s.length()) r = Val.INT(s.charAt(intVal(i))); }
            else if (name.equals("substring")) {
                Val i0 = args.length > 1 ? sim.get(args[1]) : null;
                if (knownInt(i0)) {
                    try {
                        if (args.length > 2 && knownInt(sim.get(args[2]))) r = Val.STR(s.substring(intVal(i0), intVal(sim.get(args[2]))));
                        else r = Val.STR(s.substring(intVal(i0)));
                    } catch (Throwable ignored) {}
                }
            } else if (name.equals("indexOf")) {
                Val i = args.length > 1 ? sim.get(args[1]) : null;
                if (i != null && i.kind.equals("str")) r = Val.INT(s.indexOf((String) i.data));
                else if (knownInt(i)) r = Val.INT(s.indexOf(intVal(i)));
            } else if (name.equals("concat")) {
                Val i = args.length > 1 ? sim.get(args[1]) : null;
                if (i != null && i.kind.equals("str")) r = Val.STR(s + i.data);
            } else if (name.equals("contains")) {
                Val i = args.length > 1 ? sim.get(args[1]) : null;
                if (i != null && i.kind.equals("str")) r = Val.INT(s.contains((String) i.data) ? 1 : 0);
            } else if (name.equals("startsWith") || name.equals("endsWith")) {
                Val i = args.length > 1 ? sim.get(args[1]) : null;
                if (i != null && i.kind.equals("str")) r = Val.INT(name.equals("startsWith") ? (s.startsWith((String) i.data) ? 1 : 0) : (s.endsWith((String) i.data) ? 1 : 0));
            } else if (name.equals("replace")) {
                Val o = args.length > 1 ? sim.get(args[1]) : null;
                Val n = args.length > 2 ? sim.get(args[2]) : null;
                if (o != null && o.kind.equals("str") && n != null && n.kind.equals("str")) r = Val.STR(s.replace((String) o.data, (String) n.data));
            } else if (name.equals("equals") || name.equals("equalsIgnoreCase")) {
                Val o = args.length > 1 ? sim.get(args[1]) : null;
                String os = (o != null && o.kind.equals("str")) ? (String) o.data : null;
                if (os != null) r = Val.INT(name.equals("equals") ? (s.equals(os) ? 1 : 0) : (s.equalsIgnoreCase(os) ? 1 : 0));
            }
        } else if (cls.equals("Ljava/lang/reflect/Method;") && name.equals("getReturnType")) {
            r = Val.SYMC();
        } else if (cls.equals("Ljava/lang/Class;") && name.equals("getName")) {
            Val rc = args.length > 0 ? sim.get(args[0]) : null;
            r = isClassLike(rc) ? Val.STR("") : null;
        } else if (cls.equals("Ljava/lang/Class;") && name.equals("isPrimitive")) {
            Val rc = args.length > 0 ? sim.get(args[0]) : null;
            r = isClassLike(rc) ? Val.INT(0) : null;
        } else if (st.classes.containsKey(cls)) {
            List<Val> vals = new ArrayList<>();
            for (int a : args) vals.add(sim.get(a));
            Method mm = findMethod(st, cls, name, args.length);
            if (mm != null && isStatic(mm)) {
                r = interpret(st, mm, vals, depth + 1);
            }
        }
        sim.setPending(r);
    }

    // ---------------- 纯指令（常量/移动/数组/算术） ----------------
    static boolean pureOp(Sim sim, Instruction insn) {
        Opcode op = insn.getOpcode();
        if (op == Opcode.NOP || op == Opcode.MONITOR_ENTER || op == Opcode.MONITOR_EXIT) return true;
        if (insn instanceof ArrayPayload || insn instanceof SwitchPayload) return true;

        if (insn instanceof Instruction11n) {
            Instruction11n i = (Instruction11n) insn; sim.set(i.getRegisterA(), Val.INT(i.getNarrowLiteral())); return true;
        }
        if (insn instanceof Instruction21s) {
            Instruction21s i = (Instruction21s) insn;
            sim.set(i.getRegisterA(), (op == Opcode.CONST_WIDE_16) ? Val.LONG(i.getNarrowLiteral()) : Val.INT(i.getNarrowLiteral()));
            return true;
        }
        if (insn instanceof Instruction31i) {
            Instruction31i i = (Instruction31i) insn;
            sim.set(i.getRegisterA(), (op == Opcode.CONST_WIDE_32) ? Val.LONG(i.getNarrowLiteral()) : Val.INT(i.getNarrowLiteral()));
            return true;
        }
        if (insn instanceof Instruction21ih) {
            Instruction21ih i = (Instruction21ih) insn;
            sim.set(i.getRegisterA(), Val.INT(i.getNarrowLiteral())); return true;
        }
        if (insn instanceof Instruction21lh) {
            Instruction21lh i = (Instruction21lh) insn;
            sim.set(i.getRegisterA(), Val.LONG(i.getWideLiteral())); sim.set(i.getRegisterA() + 1, Val.LONG(i.getWideLiteral())); return true;
        }
        if (insn instanceof Instruction51l) {
            Instruction51l i = (Instruction51l) insn;
            sim.set(i.getRegisterA(), Val.LONG(i.getWideLiteral())); sim.set(i.getRegisterA() + 1, Val.LONG(i.getWideLiteral())); return true;
        }
        if (insn instanceof Instruction21c) {
            Instruction21c i = (Instruction21c) insn;
            Reference r = i.getReference();
            if (r instanceof StringReference) { sim.set(i.getRegisterA(), Val.STR(((StringReference) r).getString())); return true; }
            if (r instanceof TypeReference) {
                String t = ((TypeReference) r).getType();
                if (op == Opcode.NEW_INSTANCE) {
                    if (t.equals("Ljava/lang/StringBuilder;")) sim.set(i.getRegisterA(), new Val("sb", new StringBuilder()));
                    else sim.set(i.getRegisterA(), new Val("obj", t));
                    return true;
                }
                if (op == Opcode.CONST_CLASS) { sim.set(i.getRegisterA(), Val.CLASS(t)); return true; }
                return true; // CHECK_CAST
            }
            if (r instanceof FieldReference) {
                FieldReference fr = (FieldReference) r;
                String key = fr.getDefiningClass() + "->" + fr.getName();
                if (op == Opcode.SPUT_OBJECT || op == Opcode.SPUT || op == Opcode.SPUT_WIDE ||
                    op == Opcode.SPUT_BOOLEAN || op == Opcode.SPUT_BYTE || op == Opcode.SPUT_CHAR || op == Opcode.SPUT_SHORT) {
                    sim.st.fields.put(key, sim.get(i.getRegisterA()));
                    return true;
                }
                if (op == Opcode.SGET_OBJECT || op == Opcode.SGET || op == Opcode.SGET_WIDE ||
                    op == Opcode.SGET_BOOLEAN || op == Opcode.SGET_BYTE || op == Opcode.SGET_CHAR || op == Opcode.SGET_SHORT) {
                    Val v = sim.st.fields.get(key);
                    if (v == null && op == Opcode.SGET_OBJECT) {
                        String typeDesc = primitiveTypeField(key);
                        if (typeDesc != null) v = Val.CLASS(typeDesc);
                    }
                    sim.set(i.getRegisterA(), v != null ? v : Val.UNKNOWN());
                    return true;
                }
                return true;
            }
            return true;
        }
        if (insn instanceof Instruction31c) {
            Instruction31c i = (Instruction31c) insn;
            Reference r = i.getReference();
            if (r instanceof StringReference) { sim.set(i.getRegisterA(), Val.STR(((StringReference) r).getString())); return true; }
            if (r instanceof TypeReference) {
                if (op == Opcode.CONST_CLASS) { sim.set(i.getRegisterA(), Val.CLASS(((TypeReference) r).getType())); return true; }
                return true;
            }
            return true;
        }
        if (insn instanceof Instruction22c) {
            Instruction22c i = (Instruction22c) insn;
            Reference r = i.getReference();
            if (op == Opcode.NEW_ARRAY && r instanceof TypeReference) {
                Val size = sim.get(i.getRegisterB());
                String t = ((TypeReference) r).getType();
                if (t.equals("[B")) {
                    int n = knownInt(size) && intVal(size) >= 0 ? intVal(size) : 0;
                    if (n < 0 || n > 65536) n = 0;
                    byte[] arr = new byte[n];
                    if (knownInt(size)) {
                        for (int k = 0; k < n; k++) arr[k] = 0;
                    }
                    sim.set(i.getRegisterA(), new Val("bytes", arr));
                } else {
                    int n = knownInt(size) && intVal(size) >= 0 ? intVal(size) : 0;
                    if (n < 0 || n > 65536) n = 0;
                    List<Val> l = new ArrayList<>();
                    for (int k = 0; k < n; k++) l.add(Val.INT(0));
                    sim.set(i.getRegisterA(), new Val("array", l));
                }
                return true;
            }
            if (op == Opcode.IGET_OBJECT && r instanceof FieldReference) {
                String fn = ((FieldReference) r).getName();
                if (fn.equals("val$returnType")) sim.set(i.getRegisterA(), Val.SYMC());
            }
            return true; // iget/iput/instanceof 忽略
        }
        if (insn instanceof Instruction11x) {
            Instruction11x i = (Instruction11x) insn;
            if (op == Opcode.MOVE_RESULT || op == Opcode.MOVE_RESULT_WIDE || op == Opcode.MOVE_RESULT_OBJECT) {
                Val p = sim.takePending();
                sim.set(i.getRegisterA(), p);
                return true;
            }
            if (op == Opcode.MOVE_EXCEPTION) { sim.set(i.getRegisterA(), Val.UNKNOWN()); return true; }
            if (op == Opcode.RETURN || op == Opcode.RETURN_OBJECT || op == Opcode.RETURN_WIDE || op == Opcode.RETURN_VOID) return false;
            return true;
        }
        if (insn instanceof Instruction12x) {
            Instruction12x i = (Instruction12x) insn;
            Opcode o = op;
            if (o == Opcode.MOVE || o == Opcode.MOVE_WIDE || o == Opcode.MOVE_OBJECT) {
                Val v = sim.get(i.getRegisterB());
                sim.set(i.getRegisterA(), v);
                if (o == Opcode.MOVE_WIDE) sim.set(i.getRegisterA() + 1, v);
                return true;
            }
            Val a = sim.get(i.getRegisterA()), b = sim.get(i.getRegisterB());
            switch (o) {
                case ARRAY_LENGTH:
                    sim.set(i.getRegisterA(), (b != null && b.kind.equals("array")) ? Val.INT(((List<?>) b.data).size())
                            : (b != null && b.kind.equals("bytes")) ? Val.INT(((byte[]) b.data).length)
                            : Val.UNKNOWN());
                    return true;
                case INT_TO_LONG: sim.set(i.getRegisterA(), knownInt(a) ? Val.LONG((long) intVal(a)) : Val.UNKNOWN()); return true;
                case LONG_TO_INT: sim.set(i.getRegisterA(), knownInt(a) ? Val.INT(intVal(a)) : Val.UNKNOWN()); return true;
                case INT_TO_BYTE: sim.set(i.getRegisterA(), knownInt(a) ? Val.INT((byte) intVal(a)) : Val.UNKNOWN()); return true;
                case INT_TO_CHAR: sim.set(i.getRegisterA(), knownInt(a) ? Val.INT((char) intVal(a)) : Val.UNKNOWN()); return true;
                case INT_TO_SHORT: sim.set(i.getRegisterA(), knownInt(a) ? Val.INT((short) intVal(a)) : Val.UNKNOWN()); return true;
                case INT_TO_FLOAT: case INT_TO_DOUBLE: sim.set(i.getRegisterA(), Val.UNKNOWN()); return true;
                case NEG_INT: sim.set(i.getRegisterA(), knownInt(a) ? Val.INT(-intVal(a)) : Val.UNKNOWN()); return true;
                case NOT_INT: sim.set(i.getRegisterA(), knownInt(a) ? Val.INT(~intVal(a)) : Val.UNKNOWN()); return true;
                default: break;
            }
            int a2 = intVal(a), b2 = intVal(b);
            if (!knownInt(a) || !knownInt(b)) { sim.set(i.getRegisterA(), Val.UNKNOWN()); return true; }
            switch (o) {
                case ADD_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 + b2)); return true;
                case SUB_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 - b2)); return true;
                case MUL_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 * b2)); return true;
                case DIV_INT_2ADDR: sim.set(i.getRegisterA(), b2 == 0 ? Val.UNKNOWN() : Val.INT(a2 / b2)); return true;
                case REM_INT_2ADDR: sim.set(i.getRegisterA(), b2 == 0 ? Val.UNKNOWN() : Val.INT(a2 % b2)); return true;
                case AND_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 & b2)); return true;
                case OR_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 | b2)); return true;
                case XOR_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 ^ b2)); return true;
                case SHL_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 << (b2 & 0x1f))); return true;
                case SHR_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 >> (b2 & 0x1f))); return true;
                case USHR_INT_2ADDR: sim.set(i.getRegisterA(), Val.INT(a2 >>> (b2 & 0x1f))); return true;
                case ADD_LONG_2ADDR: case SUB_LONG_2ADDR: case MUL_LONG_2ADDR: case AND_LONG_2ADDR:
                case OR_LONG_2ADDR: case XOR_LONG_2ADDR: case REM_LONG_2ADDR: case DIV_LONG_2ADDR: {
                    long x = a2, y = b2;
                    long rr = 0;
                    switch (o) {
                        case ADD_LONG_2ADDR: rr = x + y; break;
                        case SUB_LONG_2ADDR: rr = x - y; break;
                        case MUL_LONG_2ADDR: rr = x * y; break;
                        case AND_LONG_2ADDR: rr = x & y; break;
                        case OR_LONG_2ADDR: rr = x | y; break;
                        case XOR_LONG_2ADDR: rr = x ^ y; break;
                        case DIV_LONG_2ADDR: if (y == 0) { sim.set(i.getRegisterA(), Val.UNKNOWN()); return true; } rr = x / y; break;
                        case REM_LONG_2ADDR: if (y == 0) { sim.set(i.getRegisterA(), Val.UNKNOWN()); return true; } rr = x % y; break;
                        default: break;
                    }
                    sim.set(i.getRegisterA(), Val.LONG(rr)); sim.set(i.getRegisterA() + 1, Val.LONG(rr)); return true;
                }
                default: break;
            }
            return true;
        }
        if (insn instanceof Instruction22x) {
            Instruction22x i = (Instruction22x) insn;
            sim.set(i.getRegisterA(), sim.get(i.getRegisterB()));
            return true;
        }
        if (insn instanceof Instruction32x) {
            Instruction32x i = (Instruction32x) insn;
            sim.set(i.getRegisterA(), sim.get(i.getRegisterB()));
            return true;
        }
        if (insn instanceof Instruction22b) {
            Instruction22b i = (Instruction22b) insn;
            Val src = sim.get(i.getRegisterB());
            int b = i.getNarrowLiteral();
            int a2 = intVal(src);
            if (!knownInt(src)) { sim.set(i.getRegisterA(), Val.UNKNOWN()); return true; }
            switch (op) {
                case ADD_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(a2 + b)); return true;
                case RSUB_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(b - a2)); return true;
                case MUL_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(a2 * b)); return true;
                case DIV_INT_LIT8: sim.set(i.getRegisterA(), b == 0 ? Val.UNKNOWN() : Val.INT(a2 / b)); return true;
                case REM_INT_LIT8: sim.set(i.getRegisterA(), b == 0 ? Val.UNKNOWN() : Val.INT(a2 % b)); return true;
                case AND_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(a2 & b)); return true;
                case OR_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(a2 | b)); return true;
                case XOR_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(a2 ^ b)); return true;
                case SHL_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(a2 << (b & 0x1f))); return true;
                case SHR_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(a2 >> (b & 0x1f))); return true;
                case USHR_INT_LIT8: sim.set(i.getRegisterA(), Val.INT(a2 >>> (b & 0x1f))); return true;
                default: break;
            }
            return true;
        }
        if (insn instanceof Instruction22s) {
            Instruction22s i = (Instruction22s) insn;
            Val src = sim.get(i.getRegisterB());
            int b = i.getNarrowLiteral();
            int a2 = intVal(src);
            if (!knownInt(src)) { sim.set(i.getRegisterA(), Val.UNKNOWN()); return true; }
            switch (op) {
                case ADD_INT_LIT16: sim.set(i.getRegisterA(), Val.INT(a2 + b)); return true;
                case RSUB_INT: sim.set(i.getRegisterA(), Val.INT(b - a2)); return true;
                case MUL_INT_LIT16: sim.set(i.getRegisterA(), Val.INT(a2 * b)); return true;
                case DIV_INT_LIT16: sim.set(i.getRegisterA(), b == 0 ? Val.UNKNOWN() : Val.INT(a2 / b)); return true;
                case REM_INT_LIT16: sim.set(i.getRegisterA(), b == 0 ? Val.UNKNOWN() : Val.INT(a2 % b)); return true;
                case AND_INT_LIT16: sim.set(i.getRegisterA(), Val.INT(a2 & b)); return true;
                case OR_INT_LIT16: sim.set(i.getRegisterA(), Val.INT(a2 | b)); return true;
                case XOR_INT_LIT16: sim.set(i.getRegisterA(), Val.INT(a2 ^ b)); return true;
                default: break;
            }
            return true;
        }
        if (insn instanceof Instruction23x) {
            Instruction23x i = (Instruction23x) insn;
            int a = i.getRegisterA(), b = i.getRegisterB(), c = i.getRegisterC();
            Opcode o = op;
            if (o == Opcode.AGET || o == Opcode.AGET_WIDE || o == Opcode.AGET_OBJECT ||
                o == Opcode.AGET_BOOLEAN || o == Opcode.AGET_BYTE || o == Opcode.AGET_CHAR || o == Opcode.AGET_SHORT) {
                Val arr = sim.get(b);
                Val idx = sim.get(c);
                if (arr != null && arr.kind.equals("bytes")) {
                    byte[] d = (byte[]) arr.data;
                    if (knownInt(idx) && intVal(idx) >= 0 && intVal(idx) < d.length) {
                        sim.set(a, Val.INT(d[intVal(idx)]));
                    } else sim.set(a, Val.UNKNOWN());
                    return true;
                }
                if (arr != null && arr.kind.equals("array")) {
                    List<Val> d = (List<Val>) arr.data;
                    if (knownInt(idx) && intVal(idx) >= 0 && intVal(idx) < d.size()) {
                        Val v = d.get(intVal(idx));
                        sim.set(a, v != null ? v : Val.UNKNOWN());
                    } else sim.set(a, Val.UNKNOWN());
                    return true;
                }
                sim.set(a, Val.UNKNOWN()); return true;
            }
            if (o == Opcode.APUT || o == Opcode.APUT_WIDE || o == Opcode.APUT_OBJECT ||
                o == Opcode.APUT_BOOLEAN || o == Opcode.APUT_BYTE || o == Opcode.APUT_CHAR || o == Opcode.APUT_SHORT) {
                Val v = sim.get(a);
                Val arr = sim.get(b);
                Val idx = sim.get(c);
                if (arr != null && arr.kind.equals("array")) {
                    List<Val> d = (List<Val>) arr.data;
                    if (knownInt(idx) && intVal(idx) >= 0 && intVal(idx) < d.size()) {
                        d.set(intVal(idx), v != null ? v : Val.UNKNOWN());
                    }
                } else if (arr != null && arr.kind.equals("bytes")) {
                    byte[] d = (byte[]) arr.data;
                    if (knownInt(idx) && intVal(idx) >= 0 && intVal(idx) < d.length && v != null && v.kind.equals("int")) {
                        d[intVal(idx)] = (byte) intVal(v);
                    }
                }
                return true;
            }
            Val x = sim.get(b), y = sim.get(c);
            int x2 = intVal(x), y2 = intVal(y);
            if (!knownInt(x) || !knownInt(y)) { sim.set(a, Val.UNKNOWN()); return true; }
            switch (o) {
                case ADD_INT: sim.set(a, Val.INT(x2 + y2)); return true;
                case SUB_INT: sim.set(a, Val.INT(x2 - y2)); return true;
                case MUL_INT: sim.set(a, Val.INT(x2 * y2)); return true;
                case DIV_INT: sim.set(a, y2 == 0 ? Val.UNKNOWN() : Val.INT(x2 / y2)); return true;
                case REM_INT: sim.set(a, y2 == 0 ? Val.UNKNOWN() : Val.INT(x2 % y2)); return true;
                case AND_INT: sim.set(a, Val.INT(x2 & y2)); return true;
                case OR_INT: sim.set(a, Val.INT(x2 | y2)); return true;
                case XOR_INT: sim.set(a, Val.INT(x2 ^ y2)); return true;
                case SHL_INT: sim.set(a, Val.INT(x2 << (y2 & 0x1f))); return true;
                case SHR_INT: sim.set(a, Val.INT(x2 >> (y2 & 0x1f))); return true;
                case USHR_INT: sim.set(a, Val.INT(x2 >>> (y2 & 0x1f))); return true;
                case ADD_LONG: case SUB_LONG: case MUL_LONG: case DIV_LONG: case REM_LONG:
                case AND_LONG: case OR_LONG: case XOR_LONG: case SHL_LONG: case SHR_LONG: case USHR_LONG:
                case CMP_LONG: case CMPL_FLOAT: case CMPG_FLOAT: case CMPL_DOUBLE: case CMPG_DOUBLE: {
                    long xl = x2, yl = y2;
                    long rr = 0;
                    switch (o) {
                        case ADD_LONG: rr = xl + yl; break;
                        case SUB_LONG: rr = xl - yl; break;
                        case MUL_LONG: rr = xl * yl; break;
                        case DIV_LONG: if (yl == 0) { sim.set(a, Val.UNKNOWN()); return true; } rr = xl / yl; break;
                        case REM_LONG: if (yl == 0) { sim.set(a, Val.UNKNOWN()); return true; } rr = xl % yl; break;
                        case AND_LONG: rr = xl & yl; break;
                        case OR_LONG: rr = xl | yl; break;
                        case XOR_LONG: rr = xl ^ yl; break;
                        case SHL_LONG: rr = xl << (yl & 0x3f); break;
                        case SHR_LONG: rr = xl >> (yl & 0x3f); break;
                        case USHR_LONG: rr = xl >>> (yl & 0x3f); break;
                        case CMP_LONG: rr = Long.compare(xl, yl); break;
                        case CMPL_FLOAT: case CMPG_FLOAT: case CMPL_DOUBLE: case CMPG_DOUBLE: rr = 0; break;
                        default: break;
                    }
                    sim.set(a, Val.LONG(rr)); sim.set(a + 1, Val.LONG(rr)); return true;
                }
                default: break;
            }
            return true;
        }
        if (insn instanceof Instruction35c || insn instanceof Instruction3rc) {
            if (op == Opcode.FILLED_NEW_ARRAY || op == Opcode.FILLED_NEW_ARRAY_RANGE) {
                List<Val> list = new ArrayList<>();
                for (int r : argRegs(insn)) list.add(sim.get(r));
                sim.setPending(new Val("array", list));
                return true;
            }
            return false;
        }
        if (op == Opcode.FILL_ARRAY_DATA) {
            ArrayPayload ap = sim.pArr < sim.arr.size() ? sim.arr.get(sim.pArr++) : null;
            if (ap != null) {
                int a = ((OneRegisterInstruction) insn).getRegisterA();
                Val arr = sim.get(a);
                List<? extends Number> els = ap.getArrayElements();
                if (arr != null && arr.kind.equals("array")) {
                    List<Val> d = (List<Val>) arr.data;
                    int n = Math.min(d.size(), els.size());
                    for (int k = 0; k < n; k++) d.set(k, Val.INT(els.get(k).intValue()));
                } else if (arr != null && arr.kind.equals("bytes")) {
                    int n = Math.min(((byte[]) arr.data).length, els.size());
                    for (int k = 0; k < n; k++) ((byte[]) arr.data)[k] = els.get(k).byteValue();
                } else if (ap.getElementWidth() == 1) {
                    sim.set(a, new Val("bytes", arrayPayloadData(ap)));
                } else {
                    List<Val> l = new ArrayList<>();
                    for (Number n : els) l.add(Val.INT(n.intValue()));
                    sim.set(a, new Val("array", l));
                }
            }
            return true;
        }
        return false;
    }

    // ---------------- 辅助 ----------------
    static String strOfVal(Val v) {
        if (v == null) return "";
        switch (v.kind) {
            case "int": case "long": return v.data != null ? String.valueOf(v.data) : "";
            case "str": return v.data != null ? (String) v.data : "";
            case "boxed": {
                Object[] o = (Object[]) v.data;
                Object x = o[1];
                if (x instanceof Boolean) return ((Boolean) x) ? "true" : "false";
                return x != null ? String.valueOf(x) : "";
            }
            case "constret": return strOfVal((Val) v.data);
            default: return "";
        }
    }

    static String intsToUtf8(Val v) {
        if (v.kind.equals("bytes")) return new String((byte[]) v.data, StandardCharsets.UTF_8);
        if (v.kind.equals("array")) {
            List<Val> list = (List<Val>) v.data;
            byte[] b = new byte[list.size()];
            for (int i = 0; i < list.size(); i++) b[i] = (byte) intVal(list.get(i));
            return new String(b, StandardCharsets.UTF_8);
        }
        return "";
    }

    static byte[] arrayPayloadData(ArrayPayload ap) {
        int width = ap.getElementWidth();
        List<? extends Number> els = ap.getArrayElements();
        if (width == 1) {
            byte[] out = new byte[els.size()];
            for (int i = 0; i < els.size(); i++) out[i] = els.get(i).byteValue();
            return out;
        }
        byte[] out = new byte[els.size() * 4];
        for (int i = 0; i < els.size(); i++) {
            int v = els.get(i).intValue();
            out[i*4] = (byte) v;
            out[i*4+1] = (byte) (v >> 8);
            out[i*4+2] = (byte) (v >> 16);
            out[i*4+3] = (byte) (v >> 24);
        }
        return out;
    }

    static String describeVal(Val v) {
        if (v == null) return "null";
        if (v.kind.equals("str")) return "s:" + v.data;
        if (v.kind.equals("int") || v.kind.equals("long")) return v.kind + ":" + v.data;
        if (v.kind.equals("bytes")) {
            byte[] b = (byte[]) v.data;
            StringBuilder sb = new StringBuilder("bytes:");
            for (int i = 0; i < Math.min(b.length, 16); i++) sb.append(String.format("%02x", b[i]));
            sb.append("(").append(b.length).append(")");
            return sb.toString();
        }
        if (v.kind.equals("array")) {
            List<Val> l = (List<Val>) v.data;
            StringBuilder sb = new StringBuilder("array(").append(l.size()).append("):");
            for (int i = 0; i < Math.min(l.size(), 8); i++) sb.append(describeVal(l.get(i))).append(';');
            return sb.toString();
        }
        return v.kind;
    }

    static Method findMethod(State st, String cls, String name, int argc) {
        List<Method> list = st.methodIndex.get(cls + "->" + name);
        if (list == null) return null;
        for (Method m : list) {
            int n = countParamRegs(new ArrayList<>(m.getParameters()));
            if (n == argc) return m;
        }
        return null;
    }

    static String toDot(String type) {
        if (type == null) return "";
        String t = type;
        if (t.startsWith("L") && t.endsWith(";")) t = t.substring(1, t.length() - 1);
        return t.replace('/', '.');
    }

    static String simpleName(String type) {
        String t = type;
        if (t.startsWith("L") && t.endsWith(";")) t = t.substring(1, t.length() - 1);
        int slash = t.lastIndexOf('/');
        return slash >= 0 ? t.substring(slash + 1) : t;
    }

    // ---------------- 扫描：挂钩点捕获 ----------------
    static void scanAll(State st) {
        for (ClassDef cls : st.classes.values()) {
            for (Method m : cls.getMethods()) {
                if (m.getImplementation() == null) continue;
                scanMethod(st, cls, m);
            }
        }
    }

    static void scanMethod(State st, ClassDef cls, Method m) {
        MethodImplementation impl = m.getImplementation();
        int regCount = impl.getRegisterCount();
        Sim sim = new Sim(st, regCount);
        collectPayloads(impl, sim);

        List<MethodParameter> params = new ArrayList<>(m.getParameters());
        int p0 = regCount - countParamRegs(params);
        int ri = p0;
        for (MethodParameter p : params) {
            sim.set(ri, new Val("param", p.getType()));
            String t = p.getType();
            if (t.equals("J") || t.equals("D")) sim.set(ri + 1, sim.get(ri));
            ri += (t.equals("J") || t.equals("D")) ? 2 : 1;
        }

        String clsType = cls.getType();
        String currentPkg = null;

        for (Instruction insn : impl.getInstructions()) {
            Opcode op = insn.getOpcode();
            if (insn instanceof ArrayPayload || insn instanceof SwitchPayload) continue;

            // LoadPackageParam.packageName
            if (insn instanceof Instruction22c && op == Opcode.IGET_OBJECT) {
                Instruction22c i = (Instruction22c) insn;
                Reference r = i.getReference();
                if (r instanceof FieldReference) {
                    FieldReference fr = (FieldReference) r;
                    if (fr.getName().equals("packageName") &&
                        fr.getDefiningClass().equals("Lde/robv/android/xposed/callbacks/XC_LoadPackage$LoadPackageParam;")) {
                        sim.set(i.getRegisterA(), new Val("pkgname", null));
                        continue;
                    }
                }
            }

            if (pureOp(sim, insn)) continue;

            if ((op == Opcode.RETURN || op == Opcode.RETURN_OBJECT || op == Opcode.RETURN_WIDE) &&
                m.getName().equals("replaceHookedMethod")) {
                Instruction11x r11 = (Instruction11x) insn;
                Val v = sim.get(r11.getRegisterA());
                if (v != null && !v.kind.equals("unknown") && !v.kind.equals("null")) {
                    st.classSetResults.computeIfAbsent(clsType, k -> new ArrayList<>()).add(v);
                }
                continue;
            }

            if (!(insn instanceof Instruction35c || insn instanceof Instruction3rc)) continue;
            int[] args = argRegs(insn);
            Reference ref = refOf(insn);
            if (!(ref instanceof MethodReference)) continue;
            MethodReference mr = (MethodReference) ref;
            String c = mr.getDefiningClass();
            String name = mr.getName();

            // 包名判断：String.equals(pkgname)
            if (c.equals("Ljava/lang/String;") && name.equals("equals")) {
                if (args.length >= 2) {
                    Val a = sim.get(args[0]), b = sim.get(args[1]);
                    String s = null;
                    if (a != null && a.kind.equals("str") && b != null && b.kind.equals("pkgname")) s = (String) a.data;
                    else if (b != null && b.kind.equals("str") && a != null && a.kind.equals("pkgname")) s = (String) b.data;
                    if (s != null && !s.isEmpty()) {
                        st.classPkgChecks.computeIfAbsent(clsType, k -> new LinkedHashSet<>()).add(s);
                        currentPkg = s;
                    }
                }
                sim.setPending(null);
                continue;
            }

            // setResult
            if (c.startsWith("Lde/robv/android/xposed/XC_MethodHook$MethodHookParam;") &&
                (name.equals("setResult") || name.equals("setResultWithoutChecking"))) {
                Val v = args.length > 1 ? sim.get(args[1]) : null;
                if (v != null && !v.kind.equals("unknown") && !v.kind.equals("null")) {
                    st.classSetResults.computeIfAbsent(clsType, k -> new ArrayList<>()).add(v);
                }
                sim.setPending(null);
                continue;
            }

            // Xposed 挂钩 API
            if (c.startsWith("Lde/robv/android/xposed/")) {
                if (name.equals("findAndHookMethod") || name.equals("findAndHookConstructor") ||
                    name.equals("hookAllMethods") || name.equals("hookAllConstructors")) {
                    boolean isConstructor = name.equals("findAndHookConstructor") || name.equals("hookAllConstructors");
                    boolean strForm = isStringForm(mr);
                    HookReg reg = new HookReg();
                    reg.sourceClass = clsType;
                    reg.sourceMethod = m.getName();
                    reg.pkg = currentPkg;
                    if (isConstructor) {
                        reg.methodName = "<init>";
                        resolveClassName(reg, args, sim, 0);
                    } else if (name.equals("hookAllMethods") || name.equals("hookAllConstructors")) {
                        resolveClassName(reg, args, sim, 0);
                        if (args.length > 1) {
                            Val mth = sim.get(args[1]);
                            if (mth != null && mth.kind.equals("str")) reg.methodName = (String) mth.data;
                        }
                    } else {
                        resolveClassName(reg, args, sim, 0);
                        int mthIdx = strForm ? 2 : 1;
                        if (args.length > mthIdx) {
                            Val mth = sim.get(args[mthIdx]);
                            if (mth != null && mth.kind.equals("str")) reg.methodName = (String) mth.data;
                        }
                    }
                    int hookStart = isConstructor ? (strForm ? 3 : 2) : (name.equals("hookAllMethods") || name.equals("hookAllConstructors")) ? 2 : (strForm ? 3 : 2);
                    for (int i = hookStart; i < args.length && i < 8; i++) {
                        Val v = sim.get(args[i]);
                        if (v == null) continue;
                        if (v.kind.equals("array")) {
                            for (Object el : (List<?>) v.data) {
                                if (el instanceof Val && ((Val) el).kind.equals("obj")) {
                                    String t = (String) ((Val) el).data;
                                    if (st.classes.containsKey(t)) reg.anonClass = t;
                                } else if (el instanceof Val && ((Val) el).kind.equals("constret")) {
                                    reg.constret = (Val) ((Val) el).data;
                                }
                            }
                        } else if (v.kind.equals("obj")) {
                            String t = (String) v.data;
                            if (st.classes.containsKey(t)) reg.anonClass = t;
                        } else if (v.kind.equals("constret")) {
                            reg.constret = (Val) v.data;
                        }
                    }
                    st.regs.add(reg);
                    sim.setPending(null);
                    continue;
                }
                if (name.equals("findClass")) {
                    Val v = args.length > 0 ? sim.get(args[0]) : null;
                    if (v != null && v.kind.equals("str")) sim.setPending(Val.CLS((String) v.data));
                    else sim.setPending(Val.UNKNOWN());
                    continue;
                }
                if (name.equals("returnConstant")) {
                    Val v = args.length > 0 ? sim.get(args[0]) : null;
                    if (v != null && !v.kind.equals("unknown")) sim.setPending(new Val("constret", v));
                    else sim.setPending(Val.UNKNOWN());
                    continue;
                }
                sim.setPending(null);
                continue;
            }

            // 装箱
            if (c.startsWith("Ljava/lang/Boolean;") && name.equals("valueOf")) {
                Val v = args.length > 0 ? sim.get(args[0]) : null;
                sim.setPending(new Val("boxed", new Object[] { "Z", knownInt(v) ? (Object) (intVal(v) != 0) : null }));
                continue;
            }
            if (c.startsWith("Ljava/lang/Integer;") && name.equals("valueOf")) {
                Val v = args.length > 0 ? sim.get(args[0]) : null;
                sim.setPending(new Val("boxed", new Object[] { "I", knownInt(v) ? (Object) Integer.valueOf(intVal(v)) : null }));
                continue;
            }
            if (c.startsWith("Ljava/lang/Long;") && name.equals("valueOf")) {
                Val v = args.length > 0 ? sim.get(args[0]) : null;
                sim.setPending(new Val("boxed", new Object[] { "J", knownInt(v) ? (Object) Long.valueOf(intVal(v)) : null }));
                continue;
            }
            if (c.startsWith("Ljava/lang/String;") && name.equals("valueOf")) {
                Val v = args.length > 0 ? sim.get(args[0]) : null;
                sim.setPending(Val.STR(strOfVal(v)));
                continue;
            }

            if (c.startsWith("Ljava/lang/Integer;") && name.equals("<init>")) {
                Val v = args.length > 1 ? sim.get(args[1]) : null;
                if (args.length >= 1) sim.set(args[0], new Val("boxed", new Object[] { "I", knownInt(v) ? (Object) Integer.valueOf(intVal(v)) : null }));
                continue;
            }
            if (c.startsWith("Ljava/lang/Long;") && name.equals("<init>")) {
                Val v = args.length > 1 ? sim.get(args[1]) : null;
                if (args.length >= 1) sim.set(args[0], new Val("boxed", new Object[] { "J", knownInt(v) ? (Object) Long.valueOf(intVal(v)) : null }));
                continue;
            }
            if (c.startsWith("Ljava/lang/Boolean;") && name.equals("<init>")) {
                Val v = args.length > 1 ? sim.get(args[1]) : null;
                if (args.length >= 1) sim.set(args[0], new Val("boxed", new Object[] { "Z", knownInt(v) ? (Object) (intVal(v) != 0) : null }));
                continue;
            }

            // 反射辅助（结果映射器用）
            if (c.equals("Ljava/lang/reflect/Method;") && name.equals("getReturnType")) {
                sim.setPending(Val.SYMC());
                continue;
            }
            if (c.equals("Ljava/lang/Class;") && name.equals("getName")) {
                Val rc = args.length > 0 ? sim.get(args[0]) : null;
                sim.setPending(isClassLike(rc) ? Val.STR("") : Val.UNKNOWN());
                continue;
            }
            if (c.equals("Ljava/lang/Class;") && name.equals("isPrimitive")) {
                Val rc = args.length > 0 ? sim.get(args[0]) : null;
                sim.setPending(isClassLike(rc) ? Val.INT(0) : Val.UNKNOWN());
                continue;
            }

            // 模块内部调用：记录 edge + 尝试解码器解释
            if (st.classes.containsKey(c)) {
                if (currentPkg != null) {
                    st.classEdges.computeIfAbsent(c, k -> new LinkedHashSet<>()).add(currentPkg);
                }
                List<Val> vals = new ArrayList<>();
                for (int a : args) vals.add(sim.get(a));
                Method mm = findMethod(st, c, name, args.length);
                if (mm != null && isStatic(mm)) {
                    Val r = interpret(st, mm, vals, 1);
                    sim.setPending(r);
                } else {
                    sim.setPending(null);
                }
                continue;
            }

            sim.setPending(null);
        }
    }

    static void resolveClassName(HookReg reg, int[] args, Sim sim, int idx) {
        if (args.length <= idx) return;
        Val v = sim.get(args[idx]);
        if (v == null) return;
        if (v.kind.equals("str")) reg.className = toDot((String) v.data);
        else if (v.kind.equals("cls")) reg.className = toDot((String) v.data);
        else if (v.kind.equals("class")) reg.className = toDot((String) v.data);
        else if (v.kind.equals("obj")) reg.className = toDot((String) v.data);
    }

    static boolean isStringForm(MethodReference mr) {
        try {
            List<String> pts = new ArrayList<>();
            for (CharSequence s : mr.getParameterTypes()) pts.add(s.toString());
            if (!pts.isEmpty() && pts.get(0).equals("Ljava/lang/String;")) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // ---------------- 结果关联 ----------------
    static void attachResults(State st) {
        for (HookReg reg : st.regs) {
            if (reg.constret != null) { reg.resultVal = reg.constret; reg.hasResult = true; continue; }
            if (reg.anonClass != null) {
                List<Val> vals = st.classSetResults.get(reg.anonClass);
                if (vals != null && !vals.isEmpty()) {
                    reg.resultVal = vals.get(vals.size() - 1);
                    reg.hasResult = true;
                }
            }
        }
        Map<String, Integer> countPerClass = new HashMap<>();
        for (HookReg reg : st.regs) {
            if (!reg.hasResult && reg.anonClass == null && reg.constret == null) {
                countPerClass.merge(reg.sourceClass, 1, Integer::sum);
            }
        }
        for (HookReg reg : st.regs) {
            if (reg.hasResult || reg.anonClass != null || reg.constret != null) continue;
            List<Val> vals = st.classSetResults.get(reg.sourceClass);
            if (vals == null || vals.isEmpty()) continue;
            if (vals.size() == 1) {
                reg.resultVal = vals.get(0);
                reg.hasResult = true;
            } else {
                Integer cnt = countPerClass.get(reg.sourceClass);
                if (cnt != null && cnt.intValue() == vals.size()) {
                    int rank = 0;
                    for (HookReg r2 : st.regs) {
                        if (r2 == reg) break;
                        if (r2.sourceClass.equals(reg.sourceClass) && !r2.hasResult && r2.anonClass == null && r2.constret == null) rank++;
                    }
                    if (rank < vals.size()) { reg.resultVal = vals.get(rank); reg.hasResult = true; }
                }
            }
        }
    }

    static void assignPkgs(State st) {
        for (HookReg reg : st.regs) {
            if (reg.pkg != null && !reg.pkg.isEmpty()) continue;
            Set<String> own = st.classPkgChecks.get(reg.sourceClass);
            if (own != null && own.size() == 1) { reg.pkg = own.iterator().next(); continue; }
            Set<String> edges = st.classEdges.get(reg.sourceClass);
            if (edges != null && edges.size() == 1) { reg.pkg = edges.iterator().next(); }
        }
    }

    // ---------------- 组装 ----------------
    static void assemble(State st, Result res) {
        Map<String, AppConfig> configs = new LinkedHashMap<>();
        Map<String, Set<String>> seen = new HashMap<>();

        for (HookReg reg : st.regs) {
            if (!reg.hasResult || reg.className == null || reg.methodName == null) {
                if (reg.className != null) {
                    st.warnings.add("跳过无返回值配置: " + reg.className + "#" + reg.methodName);
                }
                continue;
            }
            String[] rt = resultToType(reg.resultVal);
            if (rt == null) {
                st.warnings.add("跳过无法解析返回值: " + reg.className + "#" + reg.methodName);
                continue;
            }
            String pkg = reg.pkg != null ? reg.pkg : "";
            String key = pkg + "|" + reg.sourceClass;
            AppConfig cfg = configs.get(key);
            if (cfg == null) {
                cfg = new AppConfig();
                cfg.packageName = pkg;
                cfg.appName = simpleName(reg.sourceClass);
                cfg.description = "XP模块分析";
                configs.put(key, cfg);
                res.apps.add(cfg);
                seen.put(key, new HashSet<>());
            }
            String dedup = reg.className + "#" + reg.methodName + "=" + rt[0];
            if (!seen.get(key).add(dedup)) continue;
            HookPoint hp = new HookPoint();
            hp.className = reg.className;
            hp.methodName = reg.methodName;
            hp.resultData = rt[0];
            hp.returnType = rt[1];
            hp.source = reg.sourceClass + "->" + reg.sourceMethod;
            cfg.hooks.add(hp);
        }

        res.apps.sort(Comparator.comparing(a -> a.packageName));
    }

    static String[] resultToType(Val v) {
        if (v == null) return null;
        switch (v.kind) {
            case "int": return new String[] { String.valueOf(intVal(v)), "I" };
            case "long": return new String[] { String.valueOf(v.data), "J" };
            case "str": return v.data != null ? new String[] { (String) v.data, "java.lang.String" } : null;
            case "boxed": {
                Object[] o = (Object[]) v.data;
                Object x = o[1];
                if (x == null) return null;
                if (o[0].equals("Z")) return new String[] { ((Boolean) x) ? "true" : "false", "Z" };
                if (o[0].equals("J")) return new String[] { String.valueOf(((Long) x).longValue()), "J" };
                return new String[] { String.valueOf(x), "I" };
            }
            case "constret": return resultToType((Val) v.data);
            default: return null;
        }
    }

    // ---------------- 桌面调试入口 ----------------
    public static void main(String[] args) throws Exception {
        String apk = args[0];
        Result r = analyzeApk(new File(apk));
        StringBuilder sb = new StringBuilder();
        for (String w : r.warnings) sb.append("#W ").append(w).append('\n');
        for (AppConfig c : r.apps) {
            sb.append("== ").append(c.packageName).append(" (").append(c.appName).append(") ==\n");
            for (HookPoint h : c.hooks) {
                sb.append("   ").append(h.className).append(" -> ").append(h.methodName)
                  .append(" = ").append(h.resultData).append(" :").append(h.returnType).append('\n');
            }
        }
        if (args.length > 1) {
            OutputStream os = new FileOutputStream(args[1]);
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
        }
        System.out.print(sb);
    }
}
