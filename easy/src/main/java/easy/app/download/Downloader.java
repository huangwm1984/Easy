package easy.app.download;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;

/**
 * Created by Lucio on 17/3/23.
 */

public class Downloader {

    /**
     * 开始下载任务
     * <p>
     * 需求权限：
     * //Normal Permissions
     * <uses-permission android:name="android.permission.INTERNET"/>
     * //Dangerous Permissions:
     * <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
     * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
     * </p>
     *
     * @param params
     * @return 下载任务
     */
    public static DownloadTask startRequest(Context context, DownloadTask.DownloadRequestParams params, DownloadTask.OnDownloadListener listener) {
        if (!isDownloadManagerAvailable(context))
            throw new RuntimeException("download manager is not available.");

        String url = params.mUrl;
        if (url == null || url.length() == 0) {
            throw new RuntimeException("download url invalid.");
        }

        DownloadManager dm = (DownloadManager) context.getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);

        long id = DownloadRequestCache.getExistRequestId(context, url);
        //已经存在现在任务
        if (id != DownloadTask.INVALID_DOWNLOAD_ID && id > 0) {
            boolean isTaskValid = isExistTaskIdValid(dm, id);
            //如果任务无效，则重新入队下载任务
            if (!isTaskValid) {
                //移除任务
                dm.remove(id);
                DownloadRequestCache.clearRequestId(context, url);
                //入队任务
                id = dm.enqueue(params.build());
                DownloadRequestCache.setRequestId(context, url, id);
            }
        } else {
            //任务入队执行
            id = dm.enqueue(params.build());
            DownloadRequestCache.setRequestId(context, url, id);
        }
        return new DownloadTask(dm, id, params, listener);
    }

    /**
     * 下载任务是否已完成(并且本地文件存在)
     *
     * @param context
     * @param params
     * @return
     */
    public static boolean isDownTaskSuccess(Context context, DownloadTask.DownloadRequestParams params) {
        String filePath = Environment.getExternalStoragePublicDirectory(params.mDir).toString();
        filePath = filePath + File.separator + params.mFileName;
        File file = new File(filePath);
        //本地路径文件不存在，则任务肯定不成功
        if (!file.exists() || !file.isFile()) {
            return false;
        } else {
            DownloadManager dm = (DownloadManager) context.getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
            long id = DownloadRequestCache.getExistRequestId(context, params.mUrl);
            //下载管理器存在对应任务
            if (id != DownloadTask.INVALID_DOWNLOAD_ID && id > 0) {
                //判断任务管理器中状态是否已成功
                return DownloadManagerQuery.isDownloadSuccess(dm, id);
            } else {
                return true;
            }
        }
    }

    /**
     * 存在的任务id是否有效
     *
     * @param dm
     * @param id
     * @return
     */
    private static boolean isExistTaskIdValid(DownloadManager dm, long id) {
        String fileName = DownloadManagerQuery.getFileName(dm, id);
        File file = new File(fileName);
        //如果任务id存在，文件不存在，则说明文件已被删除
        if (!file.exists() || !file.isFile()) {
            return false;
        }

        int status = DownloadManagerQuery.getStatusById(dm, id);
        //如果任务处于暂停中，尝试继续下载
        if (status == DownloadTask.TaskStatus.PAUSED) {
            try {
                DownloadManagerQuery.resumeDownload(dm, id);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        } else {
            //状态为错误或失败则是不可用任务
            return (status != DownloadTask.TaskStatus.ERROR_TASK && status != DownloadTask.TaskStatus.FAILED);
        }

    }


    /**
     * 移除下载任务
     *
     * @param context
     * @param id
     */
    public static void removeDownloadTask(Context context, String url, long id) {
        DownloadManager dm = (DownloadManager) context.getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
        dm.remove(id);
        DownloadRequestCache.clearRequestId(context, url);
    }


    /**
     * 下载管理器是否被禁用
     *
     * @param context
     * @return
     */
    public static boolean isDownloadManagerAvailable(Context context) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
                return false;
            }

            int code = context.getPackageManager().getApplicationEnabledSetting("com.android.providers.downloads");
            if (code == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    || code == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * 任务id缓存
     */
    private static final class DownloadRequestCache {

        private static final String FILE_NAME = "DOWNLOAD_ID_CACHE";

        /**
         * 获取任务ID
         *
         * @param context
         * @param url     下载地址
         * @return
         */
        public static long getExistRequestId(Context context, String url) {
            SharedPreferences sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
            return sp.getLong(url, DownloadTask.INVALID_DOWNLOAD_ID);
        }

        /**
         * 保存任务ID
         *
         * @param context
         * @param url     下载地址
         * @param id      ID
         */
        public static void setRequestId(Context context, String url, long id) {
            SharedPreferences sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putLong(url, id);
            editor.apply();
        }

        /**
         * 清理
         *
         * @param context
         * @param url
         */
        public static void clearRequestId(Context context, String url) {
            SharedPreferences sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.remove(url);
            editor.apply();
        }
    }

}
