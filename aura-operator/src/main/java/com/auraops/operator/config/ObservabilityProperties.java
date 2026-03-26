package com.auraops.operator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auraops.observability")
public class ObservabilityProperties {

    private final Loki loki = new Loki();
    private final Tempo tempo = new Tempo();
    private final Prometheus prometheus = new Prometheus();

    public Loki getLoki() {
        return loki;
    }

    public Tempo getTempo() {
        return tempo;
    }

    public Prometheus getPrometheus() {
        return prometheus;
    }

    public static class Loki {

        private boolean enabled = false;
        private String baseUrl = "http://localhost:3100";
        private String queryRangePath = "/loki/api/v1/query_range";
        private String labelName = "app";
        private Duration lookback = Duration.ofMinutes(5);
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getQueryRangePath() {
            return queryRangePath;
        }

        public void setQueryRangePath(String queryRangePath) {
            this.queryRangePath = queryRangePath;
        }

        public String getLabelName() {
            return labelName;
        }

        public void setLabelName(String labelName) {
            this.labelName = labelName;
        }

        public Duration getLookback() {
            return lookback;
        }

        public void setLookback(Duration lookback) {
            this.lookback = lookback;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Tempo {

        private boolean enabled = false;
        private String baseUrl = "http://localhost:3200";
        private String searchPath = "/api/search";
        private Duration lookback = Duration.ofMinutes(5);
        private int limit = 5;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSearchPath() {
            return searchPath;
        }

        public void setSearchPath(String searchPath) {
            this.searchPath = searchPath;
        }

        public Duration getLookback() {
            return lookback;
        }

        public void setLookback(Duration lookback) {
            this.lookback = lookback;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }

    public static class Prometheus {

        private boolean enabled = true;
        private String baseUrl = "http://localhost:9090";
        private String queryPath = "/api/v1/query";
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getQueryPath() {
            return queryPath;
        }

        public void setQueryPath(String queryPath) {
            this.queryPath = queryPath;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}
