package cn.appdream.recorder;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.support.annotation.IntDef;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Recorder view
 *
 * @author An Zewei (anzewei88[at]gmail[dot]com)
 * @since ${VERSION}
 */

public class VideoRecorderView extends SurfaceView {
    /**
     * The camera device faces the opposite direction as the device's screen.
     */
    public static final int FACING_BACK = Camera.CameraInfo.CAMERA_FACING_BACK;

    /**
     * The camera device faces the same direction as the device's screen.
     */
    public static final int FACING_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT;

    public String getFile() {
        return mFile;
    }

    /**
     * Direction the camera faces relative to device screen.
     */
    @IntDef({FACING_BACK, FACING_FRONT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Facing {
    }

    private CameraHelper mCameraHelper;
    private MediaRecorder mRecorder;//音视频录制类
    private boolean isRecording;
    private boolean mVideoSizeSet = false;
    private String mFile;

    public VideoRecorderView(Context context) {
        this(context, null);
    }

    public VideoRecorderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoRecorderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        mCameraHelper = new CameraHelper((Activity) getContext());
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.VideoRecorderView, defStyleAttr,
                R.style.Widget_VideoRecorderView);
        setFacing(a.getInt(R.styleable.VideoRecorderView_cameraOpt, FACING_BACK));
        setFlashOn(a.getBoolean(R.styleable.VideoRecorderView_flashOn, false));
        String size = a.getString(R.styleable.VideoRecorderView_videoSize);
        if (TextUtils.isEmpty(size)) {
            mVideoSizeSet = false;
        } else {
            String[] sizeArr = size.split("x");
            if (sizeArr.length != 2) {
                throw new RuntimeException("video should 123x320");
            }
            mVideoSizeSet = true;
            setVideoSize(Integer.parseInt(sizeArr[0]), Integer.parseInt(sizeArr[1]));
        }
        a.recycle();
        SurfaceHolder holder = getHolder();// 取得holder
        holder.setFormat(PixelFormat.TRANSPARENT);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder.setKeepScreenOn(true);
        holder.addCallback(mCameraHelper); // holder加入回调接口
    }

    /**
     * Chooses camera by the direction it faces.
     *
     * @param facing The camera facing. Must be either {@link #FACING_BACK} or
     *               {@link #FACING_FRONT}.
     */
    public void setFacing(@Facing int facing) {
        mCameraHelper.setCameraId(facing);
    }

    /**
     * Gets the direction that the current camera faces.
     *
     * @return The camera facing.
     */
    @Facing
    public int getFacing() {
        //noinspection WrongConstant
        return mCameraHelper.getCameraId();
    }

    public void setVideoSize(int width, int height) {
        mVideoSizeSet = true;
        mCameraHelper.setVideoSize(width, height);
    }

    public void setFlashOn(boolean flash) {
        mCameraHelper.setFlash(flash);
    }

    public boolean getFlashOn() {
        return mCameraHelper.getFlash();
    }


    public boolean startPreview(){
       return mCameraHelper.openCamera();
    }

    public void stopPreview(){
        releaseMediaRecorder();
        mCameraHelper.releaseCamera();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed && !mVideoSizeSet) {
            mCameraHelper.setVideoSize(getWidth(), getHeight());
        }
    }

    /**
     * 释放MediaRecorder
     */
    private void releaseMediaRecorder() {
        if (mRecorder != null) {
            try {
                mRecorder.stop();
            } catch (Exception e) {
            }
            mRecorder.release();
            mRecorder = null;
        }
    }


    /**
     * Start record and save mp4 to file
     */
    public boolean startRecord(String file) {
        if (isRecording)
            return true;
        if (mRecorder == null) {
            mRecorder = new MediaRecorder(); // 创建MediaRecorder
        }
        mRecorder.reset();
        mFile = file;
        try {
            if (mCameraHelper.getCamera() != null) {
                mCameraHelper.getCamera().lock();
                mCameraHelper.getCamera().unlock();
                mRecorder.setCamera(mCameraHelper.getCamera());
            }
            CamcorderProfile profile = CamcorderProfile.get(mCameraHelper.getCameraId(), CamcorderProfile.QUALITY_LOW);
            // 设置音频采集方式
            mRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            //设置视频的采集方式
            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            //设置文件的输出格式
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//aac_adif， aac_adts， output_format_rtp_avp， output_format_mpeg2ts ，webm
            //设置video的编码格式
            mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            //设置录制的视频编码比特率
            mRecorder.setVideoEncodingBitRate(2 * mCameraHelper.getSize().getWidth() * mCameraHelper.getSize().getHeight());// 比特率
            //设置录制的视频帧率,注意文档的说明:
//            mRecorder.setVideoFrameRate(30);
            //设置要捕获的视频的宽度和高度
            mRecorder.setAudioChannels(1);
            mRecorder.setAudioSamplingRate(profile.audioSampleRate / 8);
            mRecorder.setAudioEncodingBitRate(profile.audioBitRate / 8);
            mRecorder.setAudioEncoder(profile.audioCodec);
//            mSurfaceHolder.setFixedSize(320, 240);//最高只能设置640x480
//            mRecorder.setVideoSize(320, 240);//最高只能设置640x480
            //设置记录会话的最大持续时间（毫秒）
            mRecorder.setVideoSize(mCameraHelper.getSize().getWidth(), mCameraHelper.getSize().getHeight());
            mRecorder.setMaxDuration(20 * 1000);
            mRecorder.setPreviewDisplay(mCameraHelper.getSurface());
            mRecorder.setOrientationHint(mCameraHelper.getOrientation());

            //设置输出文件的路径
            mRecorder.setOutputFile(file);
            //准备录制
            mRecorder.prepare();
            //开始录制
            mRecorder.start();
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
        isRecording = true;
        return isRecording;
    }

    /**
     * Stop record
     */
    public void stopRecord() {
        try {
            //停止录制
            mRecorder.stop();
            //重置
            mRecorder.reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mCameraHelper.getCamera() != null)
            try {
                mCameraHelper.getCamera().reconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        isRecording = false;
    }

}
