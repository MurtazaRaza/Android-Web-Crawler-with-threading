package com.murti.here.webcrawler;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.apache.http.HttpStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Murti on 11-Apr-17.
 */

public class WebCrawler {


    public Context mContext;
    // SQLiteOpenHelper object for handling crawling database
    public CrawlerDB mCrawlerDB;
    // Set containing already visited URls
    public HashSet<String> crawledURL;
    // Queue for unvisited URL
    BlockingQueue<String> uncrawledURL;
    // For parallel crawling execution using ThreadPoolExecuter
    RunnableManager mManager;
    // Callback interface object to notify UI
    CrawlingCallback callback;
    // For sync of crawled and yet to crawl url lists
    Object lock;

    public WebCrawler(Context ctx, CrawlingCallback callback) {
        this.mContext = ctx;
        this.callback = callback;
        mCrawlerDB = new CrawlerDB(mContext);
        crawledURL = new HashSet<>();
        uncrawledURL = new LinkedBlockingQueue<>();
        lock = new Object();
    }

    public void clearDB() {
        try {
            SQLiteDatabase db = mCrawlerDB.getWritableDatabase();
            db.delete(CrawlerDB.TABLE_NAME, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * API to add crawler runnable in ThreadPoolExecutor workQueue
     *
     * @param Url       - Url to crawl
     * @param isRootUrl
     */
    public void startCrawlerTask(String Url, boolean isRootUrl) {
        // If it's root URl, we clear previous lists and DB table content
        if (isRootUrl) {
            crawledURL.clear();
            uncrawledURL.clear();
            clearDB();
            mManager = new RunnableManager();
        }
        // If ThreadPoolExecuter is not shutting down, add wunable to workQueue
        if (!mManager.isShuttingDown()) {
            CrawlerRunnable mTask = new CrawlerRunnable(callback, Url);
            mManager.addToCrawlingQueue(mTask);
        }
    }

    /**
     * API to shutdown ThreadPoolExecuter
     */
    public void stopCrawlerTasks() {
        mManager.cancelAllRunnable();
    }


    class CrawlerRunnable implements Runnable {

        CrawlingCallback mCallback;
        String mUrl;
        private Context mContext;
        // SQLiteOpenHelper object for handling crawling database
        private CrawlerDB mCrawlerDB;
        // Set containing already visited URls
        private HashSet<String> crawledURL;
        // Queue for unvisited URL
        BlockingQueue<String> uncrawledURL;
        // For parallel crawling execution using ThreadPoolExecuter
        RunnableManager mManager;
        // Callback interface object to notify UI
        CrawlingCallback callback;
        // For sync of crawled and yet to crawl url lists
        Object lock;

        public CrawlerRunnable(CrawlingCallback callback, String Url) {
            this.mCallback = callback;
            this.mUrl = Url;
            lock = new Object();

            //this.mContext = ctx;

            mCrawlerDB = new CrawlerDB(mContext);
            crawledURL = new HashSet<>();
            uncrawledURL = new LinkedBlockingQueue<>();
            lock = new Object();
        }

        @Override
        public void run() {
            String pageContent = retreiveHtmlContent(mUrl);

            if (!TextUtils.isEmpty(pageContent.toString())) {
                insertIntoCrawlerDB(mUrl, pageContent);
                synchronized (lock) {
                    crawledURL.add(mUrl);
                }
                mCallback.onPageCrawlingCompleted();
            } else {
                mCallback.onPageCrawlingFailed(mUrl, -1);
            }

            if (!TextUtils.isEmpty(pageContent.toString())) {
                // START
                // JSoup Library used to filter urls from html body
                Document doc = Jsoup.parse(pageContent.toString());

                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    String extractedLink = link.attr("href");
                    if (!TextUtils.isEmpty(extractedLink)) {
                        synchronized (lock) {
                            if (!crawledURL.contains(extractedLink))
                                uncrawledURL.add(extractedLink);
                        }

                    }
                }
                // End JSoup
            }
            // Send msg to handler that crawling for this url is finished
            // start more crawling tasks if queue is not empty
            mHandler.sendEmptyMessage(0);

        }

        private String retreiveHtmlContent(String Url) {
            URL httpUrl = null;
            try {
                httpUrl = new URL(Url);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            int responseCode = HttpStatus.SC_OK;
            StringBuilder pageContent = new StringBuilder();
            try {
                if (httpUrl != null) {
                    HttpURLConnection conn = (HttpURLConnection) httpUrl
                            .openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    responseCode = conn.getResponseCode();
                    if (responseCode != HttpStatus.SC_OK) {
                        throw new IllegalAccessException(
                                " http connection failed");
                    }
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    String line = null;
                    while ((line = br.readLine()) != null) {
                        pageContent.append(line);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                mCallback.onPageCrawlingFailed(Url, -1);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                mCallback.onPageCrawlingFailed(Url, responseCode);
            }

            return pageContent.toString();
        }
            /*
              API to clear previous content of crawler DB table
             */

        public void clearDB() {
            try {
                SQLiteDatabase db = mCrawlerDB.getWritableDatabase();
                db.delete(CrawlerDB.TABLE_NAME, null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * API to insert crawled url info in database
         *
         * @param mUrl   - crawled url
         * @param result - html body content of url
         */
        public void insertIntoCrawlerDB(String mUrl, String result) {

            if (TextUtils.isEmpty(result))
                return;

            SQLiteDatabase db = mCrawlerDB.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(CrawlerDB.COLUMNS_NAME.CRAWLED_URL, mUrl);
            values.put(CrawlerDB.COLUMNS_NAME.CRAWLED_PAGE_CONTENT, result);

            db.insert(CrawlerDB.TABLE_NAME, null, values);
        }

        /**
         * To manage Messages in a Thread
         */
        private Handler mHandler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(android.os.Message msg) {

                synchronized (lock) {
                    if (uncrawledURL != null && uncrawledURL.size() > 0) {
                        int availableTasks = mManager.getUnusedPoolSize();
                        while (availableTasks > 0 && !uncrawledURL.isEmpty()) {
                            startCrawlerTask(uncrawledURL.remove(), false);
                            availableTasks--;
                        }
                    }
                }

            }

            ;
        };
    }

}

