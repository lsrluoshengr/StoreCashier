package com.example.storecashier;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtil {
    private static final String TAG = "FileUtil";

    /**
     * 将 Uri 指向的图片拷贝到应用私有目录
     * @param context 上下文
     * @param uri 图片的 Uri
     * @param fileName 目标文件名
     * @return 拷贝后的绝对路径，失败返回 null
     */
    public static String copyImageToInternal(Context context, Uri uri, String fileName) {
        try {
            // 创建 images 子目录
            File imagesDir = new File(context.getFilesDir(), "images");
            if (!imagesDir.exists()) {
                imagesDir.mkdirs();
            }

            File destFile = new File(imagesDir, fileName);
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;

            OutputStream out = new FileOutputStream(destFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            out.close();
            in.close();

            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Copy image failed: " + e.getMessage());
            return null;
        }
    }
}
