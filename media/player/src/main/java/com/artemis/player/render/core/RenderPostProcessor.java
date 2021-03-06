package com.artemis.player.render.core;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.artemis.media.egl.EglCore;
import com.artemis.player.log.PlayerLog;
import com.artemis.player.sensor.ArtemisSensorListener;
import com.artemis.player.view.IRenderSurface;

/**
 * Created by xrealm on 2020/06/20.
 */
public class RenderPostProcessor implements IRenderPostProcessor, IRenderSurface,
        IRenderSurface.IRenderSurfaceListener,
        SurfaceTexture.OnFrameAvailableListener,
        ArtemisSensorListener.UpdateSensorListener {

    private static final String TAG = "[RenderPostProcessor.java]";

    private static final int STATE_RUNNING = 0x1;
    private static final int STATE_STOP = 0x1 << 1;
    private int mRenderState;

    private int mVideoWidth;
    private int mVideoHeight;

    private IRenderSurface mRenderTarget;
    private SurfaceTexture mDecodeSurfaceTexture;
    private Surface mOutputSurface;
    private boolean mSurfaceAvailable = false;

    private EglCore mEglWrapper;
    private TextureRender mTextureRender;

    private byte[] mFrameSync = new byte[1];
    private boolean[] mTextureSync = new boolean[]{false};
    private boolean mFrameAvailable = false;

    private RenderThread mRenderThread;

    private ArtemisSensorListener gyroSensor;

    public RenderPostProcessor(Context context, IRenderSurface renderSurface) {
        mRenderTarget = renderSurface;

        mEglWrapper = new EglCore();

        mTextureRender = new TextureRender();
        mRenderState = STATE_RUNNING;
        mRenderThread = new RenderThread("PlayerPostRender");
        mRenderThread.start();

        gyroSensor = new ArtemisSensorListener(context);
        gyroSensor.setUpdateSensorListener(this);
        gyroSensor.start();
    }

    @Override
    public void setRenderTarget(IRenderSurface renderTarget) {
        mRenderTarget = renderTarget;
        if (renderTarget != null) {
            mSurfaceAvailable = renderTarget.isSurfaceAvailable();

            renderTarget.removeRenderSurfaceListener(this);
            renderTarget.addRenderSurfaceListener(this);
        }
    }

    @Override
    public Surface getRenderSurface() {
        if (mOutputSurface != null) {
            return mOutputSurface;
        }
        synchronized (mTextureSync) {
            if (mOutputSurface == null) {
                while (!mTextureSync[0]) {
                    try {
                        mTextureSync.wait(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mTextureSync[0] = false;
            }
        }
        PlayerLog.d(TAG, "getRenderTarget:" + mOutputSurface);
        return mOutputSurface;
    }

    @Override
    public boolean isSurfaceAvailable() {
        return mSurfaceAvailable;
    }

    @Override
    public void setFixSize(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;

        if (mDecodeSurfaceTexture != null && width > 0 && height > 0) {
            mDecodeSurfaceTexture.setDefaultBufferSize(width, height);
        }
    }

    @Override
    public void addRenderSurfaceListener(IRenderSurfaceListener listener) {

    }

    @Override
    public void removeRenderSurfaceListener(IRenderSurfaceListener listener) {

    }

    @Override
    public void releaseRender() {
        mRenderState = STATE_STOP;
        if (mDecodeSurfaceTexture != null) {
            mDecodeSurfaceTexture.setOnFrameAvailableListener(null);
        }
        if (mRenderTarget != null) {
            mRenderTarget.removeRenderSurfaceListener(this);
        }
        synchronized (mFrameSync) {
            mFrameAvailable = false;
            mFrameSync.notify();
        }
        gyroSensor.stop();
    }

    private void createTexture() {
        int texId = mTextureRender.getTextureId();
        mDecodeSurfaceTexture = new SurfaceTexture(texId);
        mDecodeSurfaceTexture.setOnFrameAvailableListener(this);
        mDecodeSurfaceTexture.setDefaultBufferSize(mVideoWidth, mVideoHeight);
        mOutputSurface = new Surface(mDecodeSurfaceTexture);

        synchronized (mTextureSync) {
            mTextureSync[0] = true;
            mTextureSync.notify();
        }
    }

    private void makeCurrent(Surface surface) {
        mEglWrapper.makeCurrent(surface);
    }

    private void drawFrame() {
        mTextureRender.onSizeChanged(mEglWrapper.getWidth(), mEglWrapper.getHeight());
        mTextureRender.onDrawFrame();
    }

    private void release() {
        mTextureRender.destroy();
        mEglWrapper.releaseEgl();
    }

    @Override
    public void onSurfaceCreate(Object surface) {
        mSurfaceAvailable = true;
    }

    @Override
    public void onSurfaceDestroy(Object surface) {
        mSurfaceAvailable = false;
    }

    @Override
    public void onSurfaceChanged(Object surface, int width, int height) {

    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (mFrameSync) {
            mFrameAvailable = true;
            mFrameSync.notify();
        }
    }

    @Override
    public void updateSensor(float[] sensorMatrix) {

    }

    private class RenderThread extends Thread {

        public RenderThread(@NonNull String name) {
            super(name);
        }

        @Override
        public void run() {
            boolean ret = mEglWrapper.createDummyEgl();
            if (!ret) {
                return;
            }
            createTexture();
            synchronized (mFrameSync) {
                while (mRenderState == STATE_RUNNING && !mFrameAvailable) {
                    try {
                        mFrameSync.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            makeCurrent(mRenderTarget.getRenderSurface());
            while (mRenderState == STATE_RUNNING) {
                try {
                    mDecodeSurfaceTexture.updateTexImage();
                } catch (Exception e) {
                    PlayerLog.e(TAG, "updateTexImage exception: " + e.getCause());
                }
                drawFrame();
                mEglWrapper.swapBuffer();
            }
            release();
        }
    }
}
