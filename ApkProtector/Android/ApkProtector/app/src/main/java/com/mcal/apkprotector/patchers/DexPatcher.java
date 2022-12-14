package com.mcal.apkprotector.patchers;

import static com.mcal.apkprotector.patchers.ManifestPatcher.customApplication;
import static com.mcal.apkprotector.patchers.ManifestPatcher.customApplicationName;
import static com.mcal.apkprotector.patchers.ManifestPatcher.packageName;

import android.content.Context;

import androidx.annotation.NonNull;

import com.mcal.apkprotector.data.Constants;
import com.mcal.apkprotector.data.Preferences;
import com.mcal.apkprotector.utils.CommonUtils;
import com.mcal.apkprotector.utils.LoggerUtils;
import com.mcal.apkprotector.utils.file.FileUtils;

import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DexPatcher {
    @NonNull
    public static byte[] processDex(Context context) throws IOException {
        FileUtils.inputStreamAssets(context, "dexloader.dex", Constants.OUTPUT_PATH + File.separator + "dexloader.dex");

        DexBackedDexFile dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(new FileInputStream(Preferences.getDexLoader())));

        File output = new File(Constants.SMALI_PATH);
        if (!output.exists()) {
            output.mkdir();
        }
        if (!Baksmali.disassembleDexFile(dex, output, Runtime.getRuntime().availableProcessors(), new BaksmaliOptions())) {
            System.out.println("Failed dex decompile");
            FileUtils.delete(output);
            System.exit(-1);
        }
        List<File> smalies = FileUtils.getFiles(output.listFiles());
        for (File smali : smalies) {
            String smaliData = new String(FileUtils.readAllBytes(new FileInputStream(smali)));
            Pattern pattern = Pattern.compile("com.mcal.apkprotector".replaceFirst("\\.", "(.)"));
            Matcher matcher = pattern.matcher(smaliData);
            while (matcher.find()) {
                smaliData = smaliData.replaceFirst(matcher.group(), Preferences.getPackageName().replace(".", matcher.group(1)));
            }
            smaliData = smaliData.replace("$PROTECT_KEY", enc(Preferences.getProtectKey()))
                    .replace("$AppName", "Security")
                    .replace("$DEX_DIR", enc(Preferences.getFolderDexesName()))
                    .replace("$DEX_PREFIX", enc(Preferences.getPrefixDexesName()))
                    .replace("$APP_NAME", "")
                    .replace("$DEX_SUFFIX", enc(Preferences.getSuffixDexesName()))

                    .replace("$SECONDARY_DEXES", enc(Constants.SECONDARY_DEXES))
                    .replace("$MULTIDEX_LOCK", enc(Constants.MULTIDEX_LOCK))
                    .replace("$CLASSES", enc(Constants.CLASSES))
                    .replace("$ZIP", enc(Constants.ZIP))
                    .replace("$CODE_CACHE", enc(Constants.CODE_CACHE));

            if (customApplication) {
                LoggerUtils.writeLog("Custom application detected");
                if (customApplicationName.startsWith(".")) {
                    LoggerUtils.writeLog("Custom application detected");
                    if (packageName == null) {
                        LoggerUtils.writeLog("Package name is null.");
                        throw new NullPointerException("Package name is null.");
                    }
                    customApplicationName = packageName + customApplicationName;
                }
                smaliData = smaliData.replace("$REAL_APP", customApplicationName);
            } else {
                smaliData = smaliData.replace("$REAL_APP", "android.app.Application");
            }
            FileUtils.writeString(smali, smaliData);
        }
        File outputDex = new File(Constants.OUTPUT_PATH, "classes.dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outputDex.getAbsolutePath();
        List<String> list = new ArrayList<>();
        for (File smaly : smalies) {
            String absolutePath = smaly.getAbsolutePath();
            list.add(absolutePath);
        }
        if (!Smali.assemble(options, list)) {
            System.out.println("failed assemble smali");
            FileUtils.delete(output);
            FileUtils.delete(outputDex);
            System.exit(-1);
        }
        byte[] dexBytes = FileUtils.readAllBytes(new FileInputStream(outputDex));
        FileUtils.delete(output);
        FileUtils.delete(outputDex);
        return dexBytes;
    }

    @NonNull
    private static String enc(String text) {
        String str = CommonUtils.encryptStrings(text, 2);
        byte[] charset = str.getBytes(StandardCharsets.UTF_8);
        return new String(charset, StandardCharsets.UTF_8);
    }
}
