package com.example.emailapp.job.service;

import java.util.List;

import com.example.emailapp.job.dto.JobLogResponse;
import com.example.emailapp.job.entity.JobLogLevel;

/** Append-only job logging, kept separate so the worker can be tested without it. */
public interface JobLogService {

    void info(Long jobId, String message);

    void warn(Long jobId, String message);

    void error(Long jobId, String message);

    void append(Long jobId, JobLogLevel level, String message);

    List<JobLogResponse> list(Long jobId, int limit);

    long count(Long jobId, JobLogLevel level);
}
