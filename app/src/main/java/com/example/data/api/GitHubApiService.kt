package com.example.data.api

import com.example.data.model.FileContentResponse
import com.example.data.model.GitTreeResponse
import com.example.data.model.PutFileRequest
import com.example.data.model.PutFileResponse
import com.example.data.model.RepoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApiService {

    @GET("repos/{owner}/{repo}")
    suspend fun getRepository(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<RepoResponse>

    @GET("repos/{owner}/{repo}/git/trees/{branch}")
    suspend fun getGitTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch", encoded = true) branch: String,
        @Query("recursive") recursive: Int = 1
    ): Response<GitTreeResponse>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String
    ): Response<FileContentResponse>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body request: PutFileRequest
    ): Response<PutFileResponse>
}
