package com.android.accessorydisplay.source;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.view.Surface;

import com.android.accessorydisplay.common.Protocol;
import com.android.accessorydisplay.common.Service;
import com.android.accessorydisplay.common.Transport;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaProjectionService extends Service {

    private static final String DISPLAY_NAME = "MediaProjection Display";

    private boolean mSinkAvailable;
    private int mSinkWidth;
    private int mSinkHeight;
    private int mSinkDensityDpi;

    private VirtualDisplayThread mVirtualDisplayThread;
    private MediaProjection mMediaProjection;

    public MediaProjectionService(Context context, Transport transport, MediaProjection projection) {
        super(context, transport, Protocol.DisplaySourceService.ID);
        mMediaProjection = projection;
    }

    @Override
    public void start() {
        super.start();

        getLogger().log("Sending MSG_QUERY.");
        getTransport().sendMessage(Protocol.DisplaySinkService.ID,
                Protocol.DisplaySinkService.MSG_QUERY, null);
    }

    @Override
    public void stop() {
        super.stop();
        handleSinkNotAvailable();
    }

    @Override
    public void onMessageReceived(int service, int what, ByteBuffer content) {
        switch (what) {
            case Protocol.DisplaySourceService.MSG_SINK_AVAILABLE: {
                getLogger().log("Received MSG_SINK_AVAILABLE");
                if (content.remaining() >= 12) {
                    final int width = content.getInt();
                    final int height = content.getInt();
                    final int densityDpi = content.getInt();
                    if (width >= 0 && width <= 4096
                            && height >= 0 && height <= 4096
                            && densityDpi >= 60 && densityDpi <= 640) {
                        handleSinkAvailable(width, height, densityDpi);
                        return;
                    }
                }
                getLogger().log("Receive invalid MSG_SINK_AVAILABLE message.");
                break;
            }

            case Protocol.DisplaySourceService.MSG_SINK_NOT_AVAILABLE: {
                getLogger().log("Received MSG_SINK_NOT_AVAILABLE");
                handleSinkNotAvailable();
                break;
            }
        }
    }

    private void handleSinkAvailable(int width, int height, int densityDpi) {
        if (mSinkAvailable && mSinkWidth == width && mSinkHeight == height
                && mSinkDensityDpi == densityDpi) {
            return;
        }

        getLogger().log("Accessory display sink available: "
                + "width=" + width + ", height=" + height
                + ", densityDpi=" + densityDpi);
        mSinkAvailable = true;
        mSinkWidth = width;
        mSinkHeight = height;
        mSinkDensityDpi = densityDpi;
        createVirtualDisplay();
    }

    private void handleSinkNotAvailable() {
        getLogger().log("Accessory display sink not available.");

        mSinkAvailable = false;
        mSinkWidth = 0;
        mSinkHeight = 0;
        mSinkDensityDpi = 0;
        releaseVirtualDisplay();
    }

    private void createVirtualDisplay() {
        releaseVirtualDisplay();

        mVirtualDisplayThread = new VirtualDisplayThread(
                mSinkWidth, mSinkHeight, mSinkDensityDpi);
        mVirtualDisplayThread.start();
    }

    private void releaseVirtualDisplay() {
        if (mVirtualDisplayThread != null) {
            mVirtualDisplayThread.quit();
            mVirtualDisplayThread = null;
        }
    }

    private final class VirtualDisplayThread extends Thread {
        private static final int TIMEOUT_USEC = 1000000;

        private final int mWidth;
        private final int mHeight;
        private final int mDensityDpi;

        private volatile boolean mQuitting;

        public VirtualDisplayThread(int width, int height, int densityDpi) {
            mWidth = width;
            mHeight = height;
            mDensityDpi = densityDpi;
        }

        private static final int BIT_RATE = 5000000;
        private static final int FRAME_RATE = 30;
        private static final int I_FRAME_INTERVAL = 15;

        @Override
        public void run() {
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
            format.setInteger("prepend-sps-pps-to-idr-frames",1);
            /*format.setInteger("intra-refresh-mode",1);
            int mbs = (((mWidth + 15) / 16) * ((mHeight + 15) / 16) * 10) / 100;
            format.setInteger("intra-refresh-CIR-mbs", mbs);*/
            MediaCodec codec;
            try {
                codec = MediaCodec.createEncoderByType("video/avc");
            } catch (IOException e) {
                throw new RuntimeException(
                        "failed to create video/avc encoder", e);
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface surface = codec.createInputSurface();
            codec.start();

            VirtualDisplay virtualDisplay = mMediaProjection.createVirtualDisplay(
                    DISPLAY_NAME, mWidth, mHeight, mDensityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null);
            if (virtualDisplay != null) {
                stream(codec);
                virtualDisplay.release();
            }

            codec.signalEndOfInputStream();
            codec.stop();
        }

        public void quit() {
            mQuitting = true;
        }

        private void stream(MediaCodec codec) {
            BufferInfo info = new BufferInfo();
            while (!mQuitting) {
                int index = codec.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (index >= 0) {
                    ByteBuffer buffer = codec.getOutputBuffer(index);
                    buffer.limit(info.offset + info.size);
                    buffer.position(info.offset);

                    getTransport().sendMessage(Protocol.DisplaySinkService.ID,
                            Protocol.DisplaySinkService.MSG_CONTENT, buffer);
                    codec.releaseOutputBuffer(index, false);
                } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    getLogger().log("Codec dequeue buffer timed out.");
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {}
            }
        }
    }
}
