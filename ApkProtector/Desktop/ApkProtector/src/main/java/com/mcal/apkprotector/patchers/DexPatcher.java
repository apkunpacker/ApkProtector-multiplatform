package com.mcal.apkprotector.patchers;

import com.mcal.apkprotector.data.Constants;
import com.mcal.apkprotector.data.Preferences;
import com.mcal.apkprotector.utils.CommonUtils;
import com.mcal.apkprotector.utils.FileUtils;
import com.mcal.apkprotector.utils.LoggerUtils;
import org.jetbrains.annotations.NotNull;
import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.mcal.apkprotector.patchers.ManifestPatcher.*;

public class DexPatcher {
    public static byte @NotNull [] patchDex(DexBackedDexFile dex) throws IOException {
        File output = new File(Constants.SMALI_PATH);
        if (!Baksmali.disassembleDexFile(dex, output, Runtime.getRuntime().availableProcessors(), new BaksmaliOptions())) {
            System.out.println("Failed dex decompile");
            FileUtils.delete(output);
            System.exit(-1);
        }
        List<File> smalies = FileUtils.getFiles(output.listFiles());
        for (File smali : smalies) {
            String smaliData = new String(Files.readAllBytes(Paths.get(smali.getAbsolutePath())));
            Pattern pattern = Pattern.compile("com.mcal.apkprotector".replaceFirst("\\.", "(.)"));
            Matcher matcher = pattern.matcher(smaliData);
            while (matcher.find()) {
                smaliData = smaliData.replaceFirst(matcher.group(), Preferences.getPackageName().replace(".", matcher.group(1)));
            }
            smaliData = smaliData.replace("$PROTECT_KEY", enc(Preferences.getProtectKey()))
                    
                    //.replace("$DATA", CommonUtils.encryptStrings(Security.write(Constants.RELEASE_PATH + File.separator + "app-temp.apk"), 2))
                    .replace("$DATA", "")
                    .replace("$AppName", "Security")
                    .replace("$DEX_DIR", enc(Preferences.getDexDir()))
                    .replace("$DEX_PREFIX", enc(Preferences.getDexPrefix()))
                    .replace("$APP_NAME", "")
                    .replace("$DEX_SUFFIX", enc(Preferences.getDexSuffix()))

                    .replace("$SECONDARY_DEXES", enc(Constants.SECONDARY_DEXES))
                    .replace("$MULTIDEX_LOCK", enc(Constants.MULTIDEX_LOCK))
                    .replace("$CLASSES", enc(Constants.CLASSES))
                    .replace("$ZIP", enc(Constants.ZIP))
                    .replace("$CODE_CACHE", enc(Constants.CODE_CACHE))

                    .replace("ProtectApplication", Preferences.getProxyAppName());
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
            //Files.writeString(Paths.get(smali.getAbsolutePath()), smaliData, StandardOpenOption.WRITE);
            FileUtils.givenUsingJava7_whenWritingToFile_thenCorrect(smali.getAbsolutePath(), smaliData);
        }
        File outputDex = new File(Constants.OUTPUT_PATH, "classes.dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outputDex.getAbsolutePath();
        if (!Smali.assemble(options, smalies
                .stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList()))) {
            System.out.println("failed assemble smali");
            FileUtils.delete(output);
            FileUtils.delete(outputDex);
            System.exit(-1);
        }
        byte[] dexBytes = Files.readAllBytes(Paths.get(outputDex.getAbsolutePath()));
        FileUtils.delete(output);
        FileUtils.delete(outputDex);
        return dexBytes;
    }

    private static @NotNull
    String enc(String text) {
        String str = CommonUtils.encryptStrings(text, 2);
        byte[] charset = str.getBytes(StandardCharsets.UTF_8);
        return new String(charset, StandardCharsets.UTF_8);
    }
}