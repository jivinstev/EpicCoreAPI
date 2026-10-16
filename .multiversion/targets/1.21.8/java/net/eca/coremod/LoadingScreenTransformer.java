// Fork multi-version layer, 1.21.8 copy of the authors' transformer. FML 9 (9.0.18) moved the early loading screen's frame
// paint from DisplayWindow.paintFramebuffer() (FML 4/7, reading DisplayWindow.context) to
// earlydisplay/render/LoadingScreenRenderer.renderToFramebuffer()V (javap 9.0.18): GlState snapshot, framebuffer.activate(),
// letterboxed viewport, GlState.clearColor(theme background), glClear(16640), the theme's elements,
// framebuffer.deactivate(), GlState.applySnapshot. The same two edits are made there, at the clear:
//  * the Pro intro callback (System property net.eca.pro.intro.callback, a Predicate) runs first, drawing into the
//    activated early framebuffer; when it drew a frame, GlState is re-read from OpenGL (the intro binds its own program,
//    VAO, texture and blend) and the method jumps to its framebuffer.deactivate() tail, skipping the theme's elements, as
//    1.21.1 returned from paintFramebuffer; the callback is handed the LoadingScreenRenderer (the intro does not read it);
//  * glClear(16640) becomes the 32-band vertical gradient (Enable Custom Loading Background), over the early framebuffer's
//    width()/height() (FML 4's DisplayContext.scaledWidth/scaledHeight was that framebuffer's size), through GlState's
//    scissorTest/scissorBox/clearColor so FML's cached GL state stays true.
// The nested class names are the authors' (EcaTransformationService preloads them by name).
package net.eca.coremod;

import net.eca.coremod.EarlyLogWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.IincInsnNode;

import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

/**
 * Replaces the early clear colour with a vertical gradient and invokes an optional intro callback.
 */
public final class LoadingScreenTransformer implements ClassFileTransformer {

    public static final String TARGET_CLASS = "net/neoforged/fml/earlydisplay/render/LoadingScreenRenderer";
    public static final boolean ENABLED = true;

    private static final String PAINT_METHOD  = "renderToFramebuffer";
    private static final String PAINT_DESC    = "()V";
    private static final String FRAMEBUFFER_FIELD = "framebuffer";
    private static final String FRAMEBUFFER_TYPE  = "net/neoforged/fml/earlydisplay/render/EarlyFramebuffer";
    private static final String FRAMEBUFFER_DESC  = "L" + FRAMEBUFFER_TYPE + ";";
    private static final String GL_STATE      = "net/neoforged/fml/earlydisplay/render/GlState";
    private static final String GL            = "org/lwjgl/opengl/GL11C";
    private static final String INTRO_CALLBACK_KEY = "net.eca.pro.intro.callback";
    private static final String PREDICATE = "java/util/function/Predicate";
    private static final boolean BACKGROUND_ENABLED = readConfig();

    private static final int CLEAR_BITS      = 16640;
    private static final int BANDS           = 32;

    // locals past renderToFramebuffer's own (0..7 in 9.0.18)
    private static final int L_CALLBACK = 8, L_HEIGHT = 9, L_WIDTH = 10, L_BAND = 11, L_Y0 = 12, L_Y1 = 13, L_T = 14;

    private static final float CR = 239f/255f, CG = 50f/255f, CB = 61f/255f;
    private static final float DR = 128f/255f - CR, DG = -CG, DB = 128f/255f - CB;

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (TARGET_CLASS.equals(className)) {
            return transform(classfileBuffer);
        }
        return null;
    }

    public static byte[] transform(byte[] classfileBuffer) {
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new SafeWriter(cr, ClassWriter.COMPUTE_FRAMES);
            DisplayWindowVisitor cv = new DisplayWindowVisitor(cw);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);

            if (cv.transformed) {
                EarlyLogWriter.info("[LoadingScreenTransformer] Transformed early loading renderer");
                return cw.toByteArray();
            }
            return null;
        } catch (Throwable t) {
            EarlyLogWriter.error("[LoadingScreenTransformer] Failed to transform LoadingScreenRenderer", t);
            return null;
        }
    }

    private static class DisplayWindowVisitor extends ClassVisitor {
        boolean transformed;

        DisplayWindowVisitor(ClassWriter cw) {
            super(Opcodes.ASM9, cw);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
            if (PAINT_METHOD.equals(name) && PAINT_DESC.equals(desc)) {
                return new PaintMethodVisitor(this, mv, access, name, desc, sig, ex);
            }
            return mv;
        }
    }

    private static class PaintMethodVisitor extends MethodNode {
        private final DisplayWindowVisitor owner;
        private final MethodVisitor next;

        PaintMethodVisitor(DisplayWindowVisitor owner, MethodVisitor next, int access, String name, String desc,
                           String sig, String[] ex) {
            super(Opcodes.ASM9, access, name, desc, sig, ex);
            this.owner = owner;
            this.next = next;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            AbstractInsnNode clearPush = null;
            AbstractInsnNode clearCall = null;
            LabelNode tail = null;
            for (AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (clearPush == null && insn.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) insn).operand == CLEAR_BITS) {
                    AbstractInsnNode call = nextReal(insn);
                    if (call instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKESTATIC
                            && "glClear".equals(m.name) && "(I)V".equals(m.desc)) {
                        clearPush = insn;
                        clearCall = call;
                    }
                } else if (tail == null && insn instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && FRAMEBUFFER_TYPE.equals(m.owner) && "deactivate".equals(m.name) && "()V".equals(m.desc)) {
                    AbstractInsnNode field = prevReal(insn);
                    AbstractInsnNode self = field == null ? null : prevReal(field);
                    if (field instanceof FieldInsnNode f && FRAMEBUFFER_FIELD.equals(f.name)
                            && self instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0) {
                        tail = new LabelNode();
                        instructions.insertBefore(self, tail);
                    }
                }
            }
            if (clearPush != null) {
                InsnList replacement = new InsnList();
                if (tail != null) {
                    emitIntroCallback(replacement, tail);
                }
                if (BACKGROUND_ENABLED) {
                    emitGradient(replacement);
                } else {
                    replacement.add(new IntInsnNode(Opcodes.SIPUSH, CLEAR_BITS));
                    replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL, "glClear", "(I)V", false));
                }
                instructions.insertBefore(clearPush, replacement);
                instructions.remove(clearPush);
                instructions.remove(clearCall);
                owner.transformed = true;
            }
            accept(next);
        }

        private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
            AbstractInsnNode n = insn.getNext();
            while (n != null && n.getOpcode() < 0) n = n.getNext();
            return n;
        }

        private static AbstractInsnNode prevReal(AbstractInsnNode insn) {
            AbstractInsnNode p = insn.getPrevious();
            while (p != null && p.getOpcode() < 0) p = p.getPrevious();
            return p;
        }

        private void emitIntroCallback(InsnList out, LabelNode tail) {
            LabelNode callbackStart = new LabelNode();
            LabelNode callbackEnd = new LabelNode();
            LabelNode callbackFailure = new LabelNode();
            LabelNode continueRendering = new LabelNode();
            tryCatchBlocks.add(new TryCatchBlockNode(callbackStart, callbackEnd, callbackFailure, "java/lang/Throwable"));

            out.add(callbackStart);
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties",
                    "()Ljava/util/Properties;", false));
            out.add(new LdcInsnNode(INTRO_CALLBACK_KEY));
            out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "get",
                    "(Ljava/lang/Object;)Ljava/lang/Object;", false));
            out.add(new VarInsnNode(Opcodes.ASTORE, L_CALLBACK));
            out.add(new VarInsnNode(Opcodes.ALOAD, L_CALLBACK));
            out.add(new TypeInsnNode(Opcodes.INSTANCEOF, PREDICATE));
            out.add(new JumpInsnNode(Opcodes.IFEQ, callbackEnd));
            out.add(new VarInsnNode(Opcodes.ALOAD, L_CALLBACK));
            out.add(new TypeInsnNode(Opcodes.CHECKCAST, PREDICATE));
            out.add(new VarInsnNode(Opcodes.ALOAD, 0));
            out.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, PREDICATE, "test", "(Ljava/lang/Object;)Z", true));
            out.add(new JumpInsnNode(Opcodes.IFEQ, callbackEnd));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_STATE, "readFromOpenGL", "()V", false));
            out.add(new JumpInsnNode(Opcodes.GOTO, tail));

            out.add(callbackEnd);
            out.add(new JumpInsnNode(Opcodes.GOTO, continueRendering));
            out.add(callbackFailure);
            out.add(new InsnNode(Opcodes.POP));
            out.add(continueRendering);
        }

        private static void framebufferSize(InsnList out, String getter) {
            out.add(new VarInsnNode(Opcodes.ALOAD, 0));
            out.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_CLASS, FRAMEBUFFER_FIELD, FRAMEBUFFER_DESC));
            out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FRAMEBUFFER_TYPE, getter, "()I", false));
        }

        private static void emitGradient(InsnList out) {
            LabelNode lblGradient = new LabelNode();
            LabelNode lblDone = new LabelNode();
            LabelNode lblLoopStart = new LabelNode();
            LabelNode lblLoopEnd = new LabelNode();

            framebufferSize(out, "height");
            out.add(new VarInsnNode(Opcodes.ISTORE, L_HEIGHT));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_HEIGHT));
            out.add(new JumpInsnNode(Opcodes.IFGT, lblGradient));
            out.add(new IntInsnNode(Opcodes.SIPUSH, CLEAR_BITS));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL, "glClear", "(I)V", false));
            out.add(new JumpInsnNode(Opcodes.GOTO, lblDone));

            out.add(lblGradient);
            framebufferSize(out, "width");
            out.add(new VarInsnNode(Opcodes.ISTORE, L_WIDTH));
            out.add(new InsnNode(Opcodes.ICONST_1));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_STATE, "scissorTest", "(Z)V", false));

            out.add(new InsnNode(Opcodes.ICONST_0));
            out.add(new VarInsnNode(Opcodes.ISTORE, L_BAND));
            out.add(lblLoopStart);
            out.add(new VarInsnNode(Opcodes.ILOAD, L_BAND));
            out.add(new IntInsnNode(Opcodes.BIPUSH, BANDS));
            out.add(new JumpInsnNode(Opcodes.IF_ICMPGE, lblLoopEnd));

            out.add(new VarInsnNode(Opcodes.ILOAD, L_HEIGHT));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_BAND));
            out.add(new InsnNode(Opcodes.IMUL));
            out.add(new IntInsnNode(Opcodes.BIPUSH, BANDS));
            out.add(new InsnNode(Opcodes.IDIV));
            out.add(new VarInsnNode(Opcodes.ISTORE, L_Y0));

            out.add(new VarInsnNode(Opcodes.ILOAD, L_HEIGHT));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_BAND));
            out.add(new InsnNode(Opcodes.ICONST_1));
            out.add(new InsnNode(Opcodes.IADD));
            out.add(new InsnNode(Opcodes.IMUL));
            out.add(new IntInsnNode(Opcodes.BIPUSH, BANDS));
            out.add(new InsnNode(Opcodes.IDIV));
            out.add(new VarInsnNode(Opcodes.ISTORE, L_Y1));

            out.add(new LdcInsnNode(2.0f));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_Y0));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_Y1));
            out.add(new InsnNode(Opcodes.IADD));
            out.add(new InsnNode(Opcodes.I2F));
            out.add(new LdcInsnNode(2.0f));
            out.add(new InsnNode(Opcodes.FDIV));
            out.add(new InsnNode(Opcodes.FMUL));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_HEIGHT));
            out.add(new InsnNode(Opcodes.I2F));
            out.add(new InsnNode(Opcodes.FDIV));
            out.add(new InsnNode(Opcodes.FCONST_1));
            out.add(new InsnNode(Opcodes.FSUB));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(F)F", false));
            out.add(new VarInsnNode(Opcodes.FSTORE, L_T));

            colorChannel(out, CR, DR);
            colorChannel(out, CG, DG);
            colorChannel(out, CB, DB);
            out.add(new InsnNode(Opcodes.FCONST_1));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_STATE, "clearColor", "(FFFF)V", false));

            out.add(new InsnNode(Opcodes.ICONST_0));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_Y0));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_WIDTH));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_Y1));
            out.add(new VarInsnNode(Opcodes.ILOAD, L_Y0));
            out.add(new InsnNode(Opcodes.ISUB));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_STATE, "scissorBox", "(IIII)V", false));

            out.add(new IntInsnNode(Opcodes.SIPUSH, CLEAR_BITS));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL, "glClear", "(I)V", false));

            out.add(new IincInsnNode(L_BAND, 1));
            out.add(new JumpInsnNode(Opcodes.GOTO, lblLoopStart));

            out.add(lblLoopEnd);
            out.add(new InsnNode(Opcodes.ICONST_0));
            out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GL_STATE, "scissorTest", "(Z)V", false));

            out.add(lblDone);
        }

        private static void colorChannel(InsnList out, float base, float delta) {
            out.add(new LdcInsnNode(base));
            out.add(new LdcInsnNode(delta));
            out.add(new VarInsnNode(Opcodes.FLOAD, L_T));
            out.add(new InsnNode(Opcodes.FMUL));
            out.add(new InsnNode(Opcodes.FADD));
        }
    }

    private static boolean readConfig() {
        try {
            Path configPath = Paths.get("config", "eca.toml");
            if (!Files.exists(configPath)) return true;

            for (String rawLine : Files.readAllLines(configPath)) {
                String line = rawLine;
                int comment = line.indexOf('#');
                if (comment >= 0) line = line.substring(0, comment);
                line = line.trim();
                if (!line.contains("=")) continue;

                int eq = line.indexOf('=');
                String key = line.substring(0, eq).trim();
                if (key.startsWith("\"") && key.endsWith("\"") && key.length() >= 2) {
                    key = key.substring(1, key.length() - 1);
                }
                if ("Enable Custom Loading Background".equals(key)) {
                    return "true".equalsIgnoreCase(line.substring(eq + 1).trim());
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    static class SafeWriter extends ClassWriter {
        SafeWriter(ClassReader cr, int flags) { super(cr, flags); }
        @Override
        protected String getCommonSuperClass(String type1, String type2) { return "java/lang/Object"; }
    }

    public LoadingScreenTransformer() {}
}
