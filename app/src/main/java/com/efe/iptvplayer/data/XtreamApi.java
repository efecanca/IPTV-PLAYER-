package com.efe.iptvplayer.data;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Xtream Codes standart panel API uçları.
 * Temel URL: http://host:port/player_api.php
 */
public interface XtreamApi {

    @GET("player_api.php")
    Call<Map<String, Object>> login(
            @Query("username") String username,
            @Query("password") String password
    );

    @GET("player_api.php")
    Call<List<Map<String, Object>>> getLiveCategories(
            @Query("username") String username,
            @Query("password") String password,
            @Query("action") String action // "get_live_categories"
    );

    @GET("player_api.php")
    Call<List<Map<String, Object>>> getLiveStreams(
            @Query("username") String username,
            @Query("password") String password,
            @Query("action") String action, // "get_live_streams"
            @Query("category_id") String categoryId
    );

    @GET("player_api.php")
    Call<List<Map<String, Object>>> getVodCategories(
            @Query("username") String username,
            @Query("password") String password,
            @Query("action") String action // "get_vod_categories"
    );

    @GET("player_api.php")
    Call<List<Map<String, Object>>> getVodStreams(
            @Query("username") String username,
            @Query("password") String password,
            @Query("action") String action, // "get_vod_streams"
            @Query("category_id") String categoryId
    );

    @GET("player_api.php")
    Call<List<Map<String, Object>>> getSeriesCategories(
            @Query("username") String username,
            @Query("password") String password,
            @Query("action") String action // "get_series_categories"
    );

    @GET("player_api.php")
    Call<List<Map<String, Object>>> getSeries(
            @Query("username") String username,
            @Query("password") String password,
            @Query("action") String action, // "get_series"
            @Query("category_id") String categoryId
    );
}
