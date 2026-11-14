package com.example.demo.application.shared.vo;

/**
 * <h2>JobId (Value Object)</h2>
 * <p>
 * 用於在應用層中唯一標識一個排程任務 (Scheduled Job)。
 * </p>
 *
 * @param value 唯一的識別字串 (通常為 UUID)
 */
public record JobId(String value) {

    public JobId {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("JobId value cannot be null or blank");
        }
    }
}
