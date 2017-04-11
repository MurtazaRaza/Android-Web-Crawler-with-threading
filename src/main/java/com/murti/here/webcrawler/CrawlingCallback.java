package com.murti.here.webcrawler;

/**
 * Created by Murti on 10-Apr-17.
 */

public interface CrawlingCallback {

    void onPageCrawlingCompleted();
    void onPageCrawlingFailed(String Url, int errorCode);
    void onCrawlingCompleted();

}
