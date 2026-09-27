package com.example.demo.application.domain.schedule.aggregate.vo;

import jakarta.persistence.Embeddable;

/**
 * <h2>任務識別碼 (Value Object)</h2>
 * <p>
 * 用於在領域模型中唯一標識一個排程任務 (Scheduled Job)。
 * 透過 Value Object 封裝，以避免將基礎型別 (String) 散落在各處，
 * 確保領域語言的清晰度與型別安全。
 * </p>
 * 
 * @param value UUID 或其他形式的唯一字串
 */
@Embeddable
public record JobId(String value) {
}