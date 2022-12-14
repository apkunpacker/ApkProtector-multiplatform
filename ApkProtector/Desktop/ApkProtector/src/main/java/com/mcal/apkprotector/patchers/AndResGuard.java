package com.mcal.apkprotector.patchers;

import com.mcal.apkprotector.data.Constants;
import com.mcal.apkprotector.data.Preferences;
import com.mcal.apkprotector.data.ResGuard;
import com.mcal.apkprotector.utils.FileUtils;
import com.tencent.mm.resourceproguard.InputParam;
import com.tencent.mm.resourceproguard.Main;
import com.tencent.mm.util.FileOperation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AndResGuard {
    public static void proguard2(File input, File output, File file, String packageName) throws IOException {
        InputParam.Builder builder = new InputParam.Builder();
        builder.setWhiteList(new ArrayList<>());
        builder.setCompressFilePattern(new ArrayList<>());
        File rules = new File(file, "proguard-resources.json");
        if (rules.exists() && rules.isFile()) {
            try {
                builder.setKeepRoot(ResGuard.resguardFile(rules).getKeepRoot());
                String fixedResName = Preferences.getResNameArscString();//ResGuard.resguardFile(rules).getFixedResName();
                if (fixedResName != null && fixedResName.length() > 0) {
                    builder.setFixedResName(fixedResName);
                }
                String mappingFile = ResGuard.resguardFile(rules).getMappingFile();
                if (mappingFile != null) {
                    File file3 = new File(file, mappingFile);
                    if (file3.exists() && file3.isFile()) {
                        builder.setMappingFile(new File(mappingFile));
                    }
                }
                List<String> whiteList = ResGuard.resguardFile(rules).getWhiteList();
                if (whiteList != null) {
                    ArrayList<String> arrayList = new ArrayList<>();
                    for (int i = 0; i < whiteList.get(0).length(); i++) {
                        arrayList.add(packageName + "." + whiteList.get(i));
                    }
                    builder.setWhiteList(arrayList);
                }
                List<String> commpressFilePattern = ResGuard.resguardFile(rules).getCommpressFilePattern();
                if (commpressFilePattern != null) {
                    ArrayList<String> arrayList = new ArrayList<>();
                    for (int i = 0; i < commpressFilePattern.get(0).length(); i++) {
                        arrayList.add(commpressFilePattern.get(i));
                    }
                    builder.setCompressFilePattern(arrayList);
                }
                builder.setUse7zip(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        File tmpApk = new File(Constants.RELEASE_PATH + File.separator + "app-temp.apk");
        if (FileUtils.copyFileStream(input, tmpApk)) {
            builder.setApkPath(tmpApk.getAbsolutePath());
            File folderMapping = new File(output, "andresguard");
            builder.setOutBuilder(folderMapping.getAbsolutePath());
            Main.gradleRun(builder.create());
            File outputApk = new File(folderMapping, "app-temp_unsigned.apk");
            if (outputApk.exists()) {
                //outputApk.renameTo(new File(Constants.RELEASE_PATH + File.separator + "app-temp-encrypted.apk"));
                FileOperation.copyFileUsingStream(outputApk, new File(Constants.RELEASE_PATH + File.separator + "app-temp-encrypted.apk"));
                for (File f : folderMapping.listFiles()) {
                    if (f.getName().endsWith(".txt")) continue;
                    FileUtils.delete(f);
                }
            }
        }
    }
}