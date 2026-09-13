package com.nightmarket.power.web;

import java.util.List;

/** 接口请求体 */
public final class Dtos {
    private Dtos() {
    }

    public record DeviceItem(String kind, String name, int ratedPowerW, Integer quantity) {
    }

    public record ApplicationRequest(String stallNo, String stallName, String zone, String stallType,
                                     List<DeviceItem> devices, boolean openFlame,
                                     boolean freezerNeeded, boolean lightingNeeded, String businessHours) {
    }

    public record DecisionRequest(String remark) {
    }

    public record ConnectionRequest(String applicationId, String socketNo, String rcdStatus,
                                    int meterInitialWh, String cablePhotoUrl, String signatureUrl,
                                    String remark) {
    }

    public record TempPowerRequest(String applicationId, String kind, String deviceName,
                                   int ratedPowerW, Integer quantity, Integer maxWaitMinutes) {
    }

    public record TempHookupRequest(String socketNo, String hookupPhotoUrl) {
    }

    public record RechargeRequest(String vendor, double amount) {
    }

    public record IncidentReportRequest(String type, String stallNo, String description, Integer severity) {
    }

    public record DispatchRequest(String action) {
    }

    public record FaultRequest(String faultPoint, boolean rectified, String rectifyPhotoUrl) {
    }

    public record ResolveRequest(String responsibility, Integer points, Integer foodLoss,
                                 Integer customerCompensation, String suspendStall,
                                 String liabilityRemark, boolean recoverNow) {
    }

    public record RiskRequest(String stallNo, boolean restrictDevices, String restrictedDevices,
                              boolean requireRecheck, boolean migrateLine, String targetBoxId,
                              String broadcast, double feeAdjustment, String reason) {
    }

    public record RecheckRequest(boolean passed, String note) {
    }

    public record WithdrawRequest(String stallNo, int meterEndWh, String remark) {
    }

    public record TransferRequest(String stallNo, String toVendor, String remark) {
    }

    public record ShareRequest(String stallNo, String otherStall, String deviceDesc) {
    }

    public record FireInspectionRequest(String stallNo, String result, String findings,
                                        Integer points, String rectifyPhotoUrl) {
    }
}
