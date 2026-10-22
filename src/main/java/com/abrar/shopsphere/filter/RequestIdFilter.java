package com.abrar.shopsphere.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter
        extends OncePerRequestFilter {

    @Value(
            "${shopsphere.observability.request-id-header:X-Request-ID}"
    )
    private String requestIdHeader;


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId =
                request.getHeader(
                        requestIdHeader
                );


        if (requestId == null
                || requestId.isBlank()) {

            requestId =
                    UUID.randomUUID().toString();
        }


        try {

            MDC.put(
                    "requestId",
                    requestId
            );


            response.setHeader(
                    requestIdHeader,
                    requestId
            );


            filterChain.doFilter(
                    request,
                    response
            );


        } finally {

            MDC.remove(
                    "requestId"
            );
        }
    }
}