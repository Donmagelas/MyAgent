package com.example.agentplatform.tools.client;

import java.util.List;
import java.util.Optional;

/**
 * 高德地图 API 客户端抽象。
 * 工具服务只依赖该接口，便于后续替换为 MCP、缓存或离线测试实现。
 */
public interface AmapClient {

    /**
     * 地址解析为经纬度。
     */
    Optional<GeoPoint> geocode(String address, String city);

    /**
     * 在指定中心点附近搜索 POI。
     */
    List<PoiCandidate> searchAround(GeoPoint center, String keyword, int radiusMeters, int limit);

    /**
     * 计算两点之间的路线耗时。
     */
    Optional<RouteMetrics> route(GeoPoint origin, GeoPoint destination, String city, String mode);

    /**
     * 经纬度点。
     */
    record GeoPoint(
            double longitude,
            double latitude,
            String formattedAddress
    ) {

        public String toLocation() {
            return longitude + "," + latitude;
        }
    }

    /**
     * 高德 POI 候选地点。
     */
    record PoiCandidate(
            String id,
            String name,
            String address,
            String location,
            String type,
            Integer distanceMeters
    ) {
    }

    /**
     * 路线耗时和距离。
     */
    record RouteMetrics(
            int durationSeconds,
            Integer distanceMeters
    ) {
    }
}
