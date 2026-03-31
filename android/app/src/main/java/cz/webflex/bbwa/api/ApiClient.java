package cz.webflex.bbwa.api;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApiClient {

    private static final String DEFAULT_BASE_URL = "http://10.0.2.2:3000";

    private static OkHttpClient client;
    private static String baseUrl = DEFAULT_BASE_URL;
    private static String authToken = "";

    private ApiClient() {
    }

    public static void configure(String url, String token) {
        baseUrl = url;
        authToken = token;
        client = null;
    }

    public static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .addInterceptor(new Interceptor() {
                        public Response intercept(Chain chain) throws IOException {
                            Request original = chain.request();
                            Request request = original.newBuilder()
                                    .header("x-api-token", authToken)
                                    .header("Content-Type", "application/json")
                                    .build();
                            return chain.proceed(request);
                        }
                    })
                    .build();
        }
        return client;
    }

    public static String getBaseUrl() {
        return baseUrl;
    }
}
