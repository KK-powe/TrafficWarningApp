package com.example.trafficwarningapp.data.network;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

/** 直接从系统文件选择器返回的Uri流式上传视频，避免把大视频整体读进内存。 */
public class ContentUriRequestBody extends RequestBody {
    private final ContentResolver resolver;
    private final Uri uri;
    private final MediaType mediaType;

    public ContentUriRequestBody(ContentResolver resolver, Uri uri, String mimeType) {
        this.resolver = resolver;
        this.uri = uri;
        this.mediaType = MediaType.parse(mimeType == null ? "video/mp4" : mimeType);
    }

    @Nullable
    @Override
    public MediaType contentType() {
        return mediaType;
    }

    @Override
    public long contentLength() {
        try (android.content.res.AssetFileDescriptor descriptor =
                     resolver.openAssetFileDescriptor(uri, "r")) {
            return descriptor == null ? -1 : descriptor.getLength();
        } catch (IOException exception) {
            return -1;
        }
    }

    @Override
    public void writeTo(@NonNull BufferedSink sink) throws IOException {
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new IOException("无法读取所选视频");
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                sink.write(buffer, 0, count);
            }
        }
    }
}
