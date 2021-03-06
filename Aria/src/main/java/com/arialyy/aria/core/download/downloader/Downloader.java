/*
 * Copyright (C) 2016 AriaLyy(https://github.com/AriaLyy/Aria)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arialyy.aria.core.download.downloader;

import com.arialyy.aria.core.common.AbsFileer;
import com.arialyy.aria.core.common.AbsThreadTask;
import com.arialyy.aria.core.common.SubThreadConfig;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DTaskWrapper;
import com.arialyy.aria.core.inf.AbsTaskWrapper;
import com.arialyy.aria.core.inf.IDownloadListener;
import com.arialyy.aria.exception.BaseException;
import com.arialyy.aria.exception.TaskException;
import com.arialyy.aria.util.ALog;
import com.arialyy.aria.util.BufferedRandomAccessFile;
import java.io.File;
import java.io.IOException;

/**
 * Created by AriaL on 2017/7/1. 文件下载器
 */
public class Downloader extends AbsFileer<DownloadEntity, DTaskWrapper> {
  private String TAG = "Downloader";

  public Downloader(IDownloadListener listener, DTaskWrapper taskWrapper) {
    super(listener, taskWrapper);
    mTempFile = new File(mEntity.getDownloadPath());
    setUpdateInterval(taskWrapper.getConfig().getUpdateInterval());
  }

  /**
   * 小于1m的文件或是任务组的子任务、线程数都是1
   */
  @Override protected int setNewTaskThreadNum() {
    int threadNum = mTaskWrapper.getConfig().getThreadNum();
    return mEntity.getFileSize() <= SUB_LEN
        || mTaskWrapper.isGroupTask()
        || threadNum == 1
        ? 1
        : threadNum;
  }

  @Override protected boolean handleNewTask() {
    if (!mRecord.isBlock) {
      if (mTempFile.exists()) {
        mTempFile.delete();
      }
      //CommonUtil.createFile(mTempFile.getPath());
    }
    BufferedRandomAccessFile file = null;
    try {
      if (mTotalThreadNum > 1 && !mRecord.isBlock) {
        file = new BufferedRandomAccessFile(new File(mTempFile.getPath()), "rwd", 8192);
        //设置文件长度
        file.setLength(mEntity.getFileSize());
      }
      return true;
    } catch (IOException e) {
      failDownload(new TaskException(TAG,
          String.format("下载失败，filePath: %s, url: %s", mEntity.getDownloadPath(),
              mEntity.getUrl()), e));
    } finally {
      if (file != null) {
        try {
          file.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return false;
  }

  /**
   * 如果使用"Content-Disposition"中的文件名，需要更新{@link #mTempFile}的路径
   */
  void updateTempFile() {
    if (!mTempFile.getPath().equals(mEntity.getDownloadPath())) {
      if (!mTempFile.exists()) {
        mTempFile = new File(mEntity.getDownloadPath());
      } else {
        boolean b = mTempFile.renameTo(new File(mEntity.getDownloadPath()));
        ALog.d(TAG, String.format("更新tempFile文件名%s", b ? "成功" : "失败"));
      }
    }
  }

  @Override protected int getType() {
    return DOWNLOAD;
  }

  @Override protected void onPostPre() {
    super.onPostPre();
    ((IDownloadListener) mListener).onPostPre(mEntity.getFileSize());
    File file = new File(mEntity.getDownloadPath());
    if (!file.getParentFile().exists()) {
      file.getParentFile().mkdirs();
    }
  }

  @Override protected AbsThreadTask selectThreadTask(SubThreadConfig<DTaskWrapper> config) {
    switch (mTaskWrapper.getRequestType()) {
      case AbsTaskWrapper.D_FTP:
      case AbsTaskWrapper.D_FTP_DIR:
        return new FtpThreadTask(mConstance, (IDownloadListener) mListener, config);
      case AbsTaskWrapper.D_HTTP:
        return new HttpThreadTask(mConstance, (IDownloadListener) mListener, config);
    }
    return null;
  }

  private void failDownload(BaseException e) {
    closeTimer();
    mConstance.isRunning = false;
    mListener.onFail(false, e);
  }
}
