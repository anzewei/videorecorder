package cn.appdream.recorder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Camera
 *
 * @author An Zewei (anzewei88[at]gmail[dot]com)
 * @since ${VERSION}
 */

public class CameraHelper implements SurfaceHolder.Callback {
    private static final SparseArray<String> FLASH_MODES = new SparseArray<>();

    static {
        FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    private int mVideoWidth = 320, mVideoHeight = 240;
    private Camera mCamera = null;
    private SurfaceHolder mSurfaceHolder;
    private int mCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Size mSize;
    private int outputOrientation = -1;
    private Activity mActivity;
    private Camera.Parameters mCameraParameters;
    private boolean mFlash;

    public CameraHelper(Activity activity) {
        mActivity = activity;
    }

    public Surface getSurface() {
        if (mSurfaceHolder != null)
            return mSurfaceHolder.getSurface();
        return null;
    }

    public int getOrientation() {
        return outputOrientation;
    }

    public Size getSize() {
        return mSize;
    }

    public Camera getCamera() {
        return mCamera;
    }

    public int getCameraId() {
        return mCameraFacing;
    }

    public void setCameraId(int cameraId) {
        mCameraFacing = cameraId;
    }

    public void setVideoSize(int width, int height) {
        mVideoHeight = height;
        mVideoWidth = width;
        if (mCamera != null)
            openCamera();
    }

    private void open() {
        if (Camera.getNumberOfCameras() == 1) {
            mCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        for (int i = 0; i < 2; i++) {
            try {
                mCamera = Camera.open(mCameraFacing);
            } catch (Throwable throwable) {
            }
            if (mCamera != null)
                break;
        }
    }

    public boolean openCamera() {
        if (mCamera != null) {
            releaseCamera();
        }
        open();
        if (mCamera == null) {
            return false;
        }
        //魅族手机无法判断手机权限
        try {
            mCameraParameters = mCamera.getParameters();
        } catch (Throwable throwable) {
            releaseCamera();
            return false;
        }
        if (mSize == null) {
            mSize = chooseOptimalSize(mVideoWidth, mVideoHeight);
        }
        setCameraDisplayOrientation(mCameraFacing, mCamera);

        mCameraParameters.setPreviewSize(mSize.width, mSize.height);
        mCameraParameters.setPreviewFormat(ImageFormat.NV21);
        mCameraParameters.setRotation(outputOrientation);
        setAutoFocusInternal();
        setFlashInternal(mFlash);
        mCamera.setParameters(mCameraParameters);
        if (mSurfaceHolder != null) {
            try {
                //设置显示
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.startPreview();
            } catch (Exception e) {
                e.printStackTrace();
                releaseCamera();
                return false;
            }
        }
        return true;
    }


    @SuppressLint("DefaultLocale")
    private Size chooseOptimalSize(int previewW, int previewH) {
        Camera.Parameters parameters = mCameraParameters;
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains("continuous-video")) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        List<Camera.Size> videoSizeList = parameters.getSupportedVideoSizes();
        List<Camera.Size> previewSizeList = parameters.getSupportedPreviewSizes();

        List<Size> listDest = new ArrayList<>();
        if (null == videoSizeList) {
            for (Camera.Size it : previewSizeList) {//没有视频分辨率列表时，用预览分辨率列表
                listDest.add(new Size(it.width, it.height));
            }
        } else {//同时支持的分辨率列表
            Map<String, Camera.Size> mapVideo = new HashMap<>();
            for (Camera.Size it : videoSizeList) {
                mapVideo.put(String.format("%dx%d", it.width, it.height), it);
            }
            for (Camera.Size it : previewSizeList) {
                if (null != mapVideo.get(String.format("%dx%d", it.width, it.height))) {
                    listDest.add(new Size(it.width, it.height));
                }
            }
        }

        Size sizeRst = null;
        Size localSize = getOptimalPreviewSize(listDest, previewW, previewH);
        if (localSize != null) {
            sizeRst = new Size(localSize.width, localSize.height);
        } else {
            sizeRst = new Size(previewW, previewH);
            Log.e("CameraManager", "设置预览失败");
        }
        return sizeRst;
    }

    private void setCameraDisplayOrientation(int cameraId, Camera camera) {
        int displayOrientation = -1;
        Camera.CameraInfo info = new Camera.CameraInfo();
        int rotation = getActivity().getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        Camera.getCameraInfo(cameraId, info);

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (info.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(displayOrientation);
        if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            outputOrientation =
                    getCameraPictureRotation(getActivity().getWindowManager()
                            .getDefaultDisplay()
                            .getOrientation());
        } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            outputOrientation = (360 - displayOrientation) % 360;
        } else {
            outputOrientation = displayOrientation;
        }
    }

    private int getCameraPictureRotation(int orientation) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraFacing, info);
        int rotation = 0;

        orientation = (orientation + 45) / 90 * 90;

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360;
        } else { // back-facing camera
            rotation = (info.orientation + orientation) % 360;
        }

        return (rotation);
    }


    private Size getOptimalPreviewSize(List<Size> localList, int w, int h) {
        Size optimalSize = null;
        try {
            ArrayList<Size> localArrayList = new ArrayList<Size>();
            for (Size localSize : localList) {
                //Log.e("---wh", localSize.width + " x " + localSize.height);
                if (localSize.width <= w && localSize.height <= h) {
                    localArrayList.add(localSize);
                }
            }
            if (localArrayList.isEmpty()) {
                Collections.sort(localArrayList, new PreviewComparator(h, w));
                optimalSize = localArrayList.get(0);
            } else optimalSize = localList.get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.e("getOptimalPreviewSize", optimalSize.width + "x" + optimalSize.height);
        return optimalSize;
    }

    void setFlash(boolean flash) {
        if (flash == mFlash) {
            return;
        }
        if (setFlashInternal(flash)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    boolean getFlash() {
        return mFlash;
    }


    public boolean isCameraOpened() {
        return mCamera != null;
    }

    /**
     * 释放相机资源
     */
    public void releaseCamera() {
        try {
            if (mCamera != null) {
                mCamera.lock();
                mCamera.release();
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            mCamera = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceHolder = holder;
        if (mCamera == null) {
            return;
        }
        try {
            //设置显示
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceHolder = null;
    }

    private Activity getActivity() {
        return mActivity;
    }

    private void setAutoFocusInternal() {
        List<String> focusModesList = mCameraParameters.getSupportedFocusModes();
        if (focusModesList.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        } else if (focusModesList.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
    }

    /**
     * @return {@code true} if {@link #mCameraParameters} was modified.
     */
    private boolean setFlashInternal(boolean flash) {
        if (isCameraOpened()) {
            if (flash) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            } else
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            return true;
        } else {
            mFlash = flash;
            return false;
        }
    }

    private static class PreviewComparator implements Comparator<Size> {
        private int h;
        private int w;

        private PreviewComparator(int h, int w) {
            this.h = h;
            this.w = w;
        }

        @Override
        public int compare(Size o1, Size o2) {
            return o2.height - o1.height + o2.width - o1.width;
        }
    }

    /**
     * 尺寸类
     */
    public static class Size {
        private int width;
        private int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        @Override
        public String toString() {
            return String.format("%dx%d", width, height);
        }
    }
}
