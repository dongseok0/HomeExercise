package paeng.dongseok.homeexercise;

import android.support.annotation.NonNull;
import android.util.Patterns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.functions.Func3;
import rx.schedulers.Schedulers;

public class Parser {
    final private Pattern pMention = Pattern.compile("@(\\w+)\\W");
    final private Pattern pEmoticon = Pattern.compile("\\((\\w{1,15})\\)");
    final private Gson    gson = new GsonBuilder().setPrettyPrinting().create();
    final private OkHttpClient client = new OkHttpClient();

    @NonNull
    public Observable<String> parse(String input) {
        return Observable.combineLatest(
                matchPattern(pMention, input, 1),
                matchPattern(pEmoticon, input, 1),
                findLinks(input),
                new Func3<List<String>, List<String>, List<JsonObject>, String>() {
                    @Override
                    public String call(List<String> mentions, List<String> emoticons, List<JsonObject> links) {
                        JsonObject result = new JsonObject();
                        result.add("mentions", gson.toJsonTree(mentions));
                        result.add("emoticons", gson.toJsonTree(emoticons));
                        result.add("links", gson.toJsonTree(links));
                        return gson.toJson(result);
                    }
                }
        );
    }

    @NonNull
    private Observable<List<String>> matchPattern(final Pattern pattern, final String input, final int group) {
        return Observable.fromCallable(new Callable<List<String>>() {
            @Override
            public List<String> call() throws Exception {
                ArrayList<String> result = new ArrayList<>();
                Matcher matcher = pattern.matcher(input);

                while (matcher.find()) {
                    result.add(matcher.group(group));
                }
                return result.size() > 0 ? result : null;
            }
        }).subscribeOn(Schedulers.computation());
    }

    @NonNull
    private Observable<List<JsonObject>> findLinks(String input) {
        return matchPattern(Patterns.WEB_URL, input, 0)
                .flatMap(new Func1<List<String>, Observable<String>>() {
                    @Override
                    public Observable<String> call(List<String> urls) {
                        return Observable.from(urls);
                    }
                })
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String url) {
                        try {
                            new URL(url);
                            return true;
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                            return false;
                        }
                    }
                })
                .flatMap(new Func1<String, Observable<JsonObject>>() {
                    @Override
                    public Observable<JsonObject> call(String url) {
                        return Observable.zip(
                                Observable.just(url),
                                fetchTitle(url),
                                new Func2<String, String, JsonObject>() {
                                    @Override
                                    public JsonObject call(String url, String title) {
                                        JsonObject link = new JsonObject();
                                        link.addProperty("url", url);
                                        link.addProperty("title", title);
                                        return link;
                                    }
                                }
                        );
                    }
                })
                .toList();
    }

    @NonNull
    private Observable<String> fetchTitle(String url) {
        return fetchPageAsString(url)
                .observeOn(Schedulers.computation())
                .flatMap(new Func1<String, Observable<String>>() {
                    @Override
                    public Observable<String> call(String html) {
                        if (html == null) {
                            return Observable.just("Failed to retrieve page.");
                        }
                        return Observable.just(Jsoup.parse(html).title());
                    }
                });
    }

    @NonNull
    private Observable<String> fetchPageAsString(final String url) {
        return Observable.fromCallable(new Callable<String>() {
            @Override
            public String call() throws Exception {
                try {
                    Request request = new Request.Builder().url(url).build();
                    Response response = client.newCall(request).execute();
                    return response.body().string();
                } catch (IOException e) {
                    return null;
                }
            }
        }).subscribeOn(Schedulers.io());
    }
}
