package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.view.PageGottenView;
import com.example.demo.application.shared.view.ScheduleJobGottenView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "分頁排程資料查詢回應")
public record PagedJobStatusSearchedResource(
        @Schema(description = "狀態碼", example = "200") String code,
        @Schema(description = "回應訊息", example = "查詢成功") String message,
        @Schema(description = "分頁資料") PageGottenView<ScheduleJobGottenView> data
) {
}
