package cn.mhook.ai;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SkillReader {

    private static final int MAX_SKILL_BYTES = 100 * 1024;

    public static String[] listSkills(Context c) {
        String[] entries = null;
        try {
            entries = c.getAssets().list("skills");
        } catch (Throwable ignored) {
        }
        if (entries == null) {
            return new String[0];
        }
        List<String> out = new ArrayList<String>();
        for (String e : entries) {
            if (hasSkill(c, e)) {
                out.add(e);
            }
        }
        Collections.sort(out);
        return out.toArray(new String[0]);
    }

    public static String readSkill(Context c, String name) {
        if (name == null) {
            return null;
        }
        try {
            InputStream is = c.getAssets().open("skills/" + name + "/SKILL.md");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = is.read(buf)) > 0) {
                total += n;
                if (total > MAX_SKILL_BYTES) {
                    bos.write(buf, 0, n - (total - MAX_SKILL_BYTES));
                    is.close();
                    return bos.toString("UTF-8") + "\n...[技能文档过长已截断]";
                }
                bos.write(buf, 0, n);
            }
            is.close();
            return bos.toString("UTF-8");
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean hasSkill(Context c, String name) {
        try {
            InputStream is = c.getAssets().open("skills/" + name + "/SKILL.md");
            is.close();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
