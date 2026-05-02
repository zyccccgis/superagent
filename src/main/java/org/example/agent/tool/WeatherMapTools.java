package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 天气和地图工具。
 * 天气使用 Open-Meteo，地图地理编码使用 OpenStreetMap Nominatim。
 */
@Component
@ManagedTool(
        name = "weather_map",
        displayName = "天气地图",
        description = "查询城市当前天气、坐标和公开地图地理编码结果",
        riskLevel = "LOW",
        instruction = "当用户询问天气、城市坐标、地点位置或需要把地点解析成经纬度时使用。",
        order = 80
)
public class WeatherMapTools {

    private static final Logger logger = LoggerFactory.getLogger(WeatherMapTools.class);
    private static final String GEOCODING_API = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_API = "https://api.open-meteo.com/v1/forecast";
    private static final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Value("${weather-map.tool.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${weather-map.tool.max-map-results:5}")
    private int maxMapResults;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        logger.info("WeatherMapTools 初始化成功, timeout: {}s, maxMapResults: {}", timeoutSeconds, maxMapResults);
    }

    @Tool(description = "Get current weather for a city or place using public geocoding and weather APIs.")
    public String getCurrentWeather(
            @ToolParam(description = "City or place name, for example: Hangzhou, Beijing, San Francisco")
            String location) {
        long startedAt = System.currentTimeMillis();
        try {
            GeoPoint point = resolveOpenMeteoLocation(location);
            URI weatherUri = URI.create(FORECAST_API
                    + "?latitude=" + point.latitude
                    + "&longitude=" + point.longitude
                    + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m"
                    + "&timezone=auto");
            JsonNode root = getJson(weatherUri);
            JsonNode current = root.path("current");

            WeatherOutput output = new WeatherOutput();
            output.success = true;
            output.location = point;
            output.temperatureCelsius = current.path("temperature_2m").asDouble();
            output.apparentTemperatureCelsius = current.path("apparent_temperature").asDouble();
            output.relativeHumidity = current.path("relative_humidity_2m").asInt();
            output.precipitation = current.path("precipitation").asDouble();
            output.weatherCode = current.path("weather_code").asInt();
            output.weatherDescription = weatherDescription(output.weatherCode);
            output.windSpeedKmh = current.path("wind_speed_10m").asDouble();
            output.observedAt = current.path("time").asText("");
            output.elapsedMs = System.currentTimeMillis() - startedAt;
            output.executedAt = Instant.now().toString();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("天气查询失败", e);
            return buildError(location, e.getMessage());
        }
    }

    @Tool(description = "Search map/geocoding results for a place and return coordinates and display names.")
    public String searchMapPlace(
            @ToolParam(description = "Place, address, POI, or city name to geocode")
            String query) {
        long startedAt = System.currentTimeMillis();
        try {
            if (query == null || query.trim().isEmpty()) {
                throw new IllegalArgumentException("query 不能为空");
            }
            int limit = Math.max(1, Math.min(maxMapResults, 10));
            URI uri = URI.create(NOMINATIM_API + "?q=" + encode(query.trim())
                    + "&format=jsonv2&limit=" + limit);
            JsonNode root = getJson(uri);

            MapSearchOutput output = new MapSearchOutput();
            output.success = true;
            output.query = query.trim();
            output.results = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    MapPlace place = new MapPlace();
                    place.displayName = item.path("display_name").asText("");
                    place.latitude = item.path("lat").asText("");
                    place.longitude = item.path("lon").asText("");
                    place.type = item.path("type").asText("");
                    place.category = item.path("category").asText("");
                    output.results.add(place);
                }
            }
            output.elapsedMs = System.currentTimeMillis() - startedAt;
            output.executedAt = Instant.now().toString();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("地图地理编码查询失败", e);
            return buildError(query, e.getMessage());
        }
    }

    private GeoPoint resolveOpenMeteoLocation(String location) throws IOException, InterruptedException {
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("location 不能为空");
        }
        URI uri = URI.create(GEOCODING_API + "?name=" + encode(location.trim())
                + "&count=1&language=zh&format=json");
        JsonNode root = getJson(uri);
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new IllegalArgumentException("未找到地点: " + location);
        }
        JsonNode first = results.get(0);
        GeoPoint point = new GeoPoint();
        point.name = first.path("name").asText(location.trim());
        point.country = first.path("country").asText("");
        point.admin1 = first.path("admin1").asText("");
        point.latitude = first.path("latitude").asDouble();
        point.longitude = first.path("longitude").asDouble();
        point.timezone = first.path("timezone").asText("");
        return point;
    }

    private JsonNode getJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", "SuperBizAgent/1.0 contact: local")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String weatherDescription(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1, 2, 3 -> "多云";
            case 45, 48 -> "雾";
            case 51, 53, 55, 56, 57 -> "毛毛雨";
            case 61, 63, 65, 66, 67 -> "降雨";
            case 71, 73, 75, 77 -> "降雪";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95, 96, 99 -> "雷暴";
            default -> "未知天气";
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String buildError(String input, String errorMessage) {
        try {
            ErrorOutput output = new ErrorOutput();
            output.success = false;
            output.input = input;
            output.error = errorMessage;
            output.executedAt = Instant.now().toString();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (IOException e) {
            return "{\"success\":false,\"error\":\"" + errorMessage + "\"}";
        }
    }

    private static class GeoPoint {
        @JsonProperty("name")
        public String name;
        @JsonProperty("country")
        public String country;
        @JsonProperty("admin1")
        public String admin1;
        @JsonProperty("latitude")
        public double latitude;
        @JsonProperty("longitude")
        public double longitude;
        @JsonProperty("timezone")
        public String timezone;
    }

    private static class WeatherOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("location")
        public GeoPoint location;
        @JsonProperty("temperature_celsius")
        public double temperatureCelsius;
        @JsonProperty("apparent_temperature_celsius")
        public double apparentTemperatureCelsius;
        @JsonProperty("relative_humidity")
        public int relativeHumidity;
        @JsonProperty("precipitation")
        public double precipitation;
        @JsonProperty("weather_code")
        public int weatherCode;
        @JsonProperty("weather_description")
        public String weatherDescription;
        @JsonProperty("wind_speed_kmh")
        public double windSpeedKmh;
        @JsonProperty("observed_at")
        public String observedAt;
        @JsonProperty("elapsed_ms")
        public long elapsedMs;
        @JsonProperty("executed_at")
        public String executedAt;
    }

    private static class MapSearchOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("query")
        public String query;
        @JsonProperty("results")
        public List<MapPlace> results;
        @JsonProperty("elapsed_ms")
        public long elapsedMs;
        @JsonProperty("executed_at")
        public String executedAt;
    }

    private static class MapPlace {
        @JsonProperty("display_name")
        public String displayName;
        @JsonProperty("latitude")
        public String latitude;
        @JsonProperty("longitude")
        public String longitude;
        @JsonProperty("category")
        public String category;
        @JsonProperty("type")
        public String type;
    }

    private static class ErrorOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("input")
        public String input;
        @JsonProperty("error")
        public String error;
        @JsonProperty("executed_at")
        public String executedAt;
    }
}
