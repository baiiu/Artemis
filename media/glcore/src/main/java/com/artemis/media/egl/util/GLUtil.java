package com.artemis.media.egl.util;

import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLES30;
import android.util.Log;

/**
 * GL工具类
 * Created by xrealm on 2020/6/27.
 */
public class GLUtil {

    private static final String TAG = "[GLUtil.java]";

    public static int generateOESTexture() {
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);

        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        return textures[0];
    }

    public static void deleteTexture(int textureId) {
        int[] textures = new int[]{textureId};
        GLES30.glDeleteTextures(1, textures, 0);
    }

    public static void deleteTextures(int n, int[] textures) {

    }

    public static void deleteTextures(int n, int[] textures, int offset) {

    }

    public static void checkGLError(String tag, String label) {
        int lastError = GLES30.GL_NO_ERROR;
        // Drain the queue of all errors.
        int error;
        while ((error = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
            Log.e(tag, label + ": glError " + error);
            lastError = error;
        }
        if (lastError != GLES30.GL_NO_ERROR) {
            throw new RuntimeException(label + ": glError " + lastError);
        }
    }
}
