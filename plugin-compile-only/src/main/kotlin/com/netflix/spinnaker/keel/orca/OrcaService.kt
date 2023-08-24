package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface OrcaService {

  @POST("ops")
  @Headers("Content-Type: application/context+json", "X-SPINNAKER-USER-ORIGIN: keel")
  suspend fun orchestrate(
    @Header("X-SPINNAKER-USER") user: String,
    @Body request: OrchestrationRequest,
  ): TaskRefResponse

  @POST("orchestrate/{pipelineConfigId}")
  @Headers("Content-Type: application/context+json", "X-SPINNAKER-USER-ORIGIN: keel")
  suspend fun triggerPipeline(
    @Header("X-SPINNAKER-USER") user: String,
    @Path("pipelineConfigId") pipelineConfigId: String,
    @Body trigger: HashMap<String, Any>,
  ): TaskRefResponse

  @GET("pipelines/{id}")
  suspend fun getPipelineExecution(
    @Path("id") id: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT,
  ): ExecutionDetailResponse

  @GET("applications/{application}/tasks")
  suspend fun getApplicationTasks(
    @Path("application") application: String,
    @Query("page") page: Int = 1,
    @Query("limit") limit: Int = 3500,
    @Query("statuses") statuses: String? = null,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT,
  ): List<ExecutionDetailResponse>

  @GET("tasks/{id}")
  suspend fun getOrchestrationExecution(
    @Path("id") id: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT,
  ): ExecutionDetailResponse

  @PUT("tasks/{id}/cancel")
  suspend fun cancelOrchestration(
    @Path("id") id: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT,
  )

  @GET("executions/correlated/{correlationId}")
  suspend fun getCorrelatedExecutions(
    @Path("correlationId") correlationId: String,
    @Header("X-SPINNAKER-USER") user: String = DEFAULT_SERVICE_ACCOUNT,
  ): List<String>

  @GET("pipelines")
  suspend fun getExecutions(
    @Query("pipelineConfigIds") pipelineConfigIds: String? = null,
    @Query("executionIds") executionIds: String? = null,
    @Query("limit") limit: Int? = 1,
    @Query("statuses") statuses: String? = null,
    @Query("expand") expand: Boolean = false,
  ): List<ExecutionDetailResponse>
}
