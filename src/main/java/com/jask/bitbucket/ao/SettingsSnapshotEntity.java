package com.jask.bitbucket.ao;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Indexed;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

/**
 * 설정 스냅샷 Active Objects entity.
 * 설정 백업/복원 및 변경 승인 워크플로우의 기반입니다.
 *
 * 요건 17: 백업/복원
 * 요건 15: 변경 승인 워크플로우
 */
@Table("JASK_SETTINGS_SNAP")
@Preload
public interface SettingsSnapshotEntity extends Entity {

    /**
     * 스냅샷 이름 (사용자 지정 또는 자동 생성)
     */
    @Indexed
    String getSnapshotName();
    void setSnapshotName(String snapshotName);

    /**
     * 스냅샷 유형: MANUAL, AUTO_BACKUP, PRE_CHANGE, SCHEDULED
     */
    String getSnapshotType();
    void setSnapshotType(String snapshotType);

    /**
     * 설정 데이터 (전체 설정의 JSON 직렬화)
     */
    @StringLength(StringLength.UNLIMITED)
    String getSettingsJson();
    void setSettingsJson(String settingsJson);

    /**
     * 포함된 섹션 목록 (콤마 구분: GLOBAL,ENGINE,MASKING 등)
     */
    String getIncludedSections();
    void setIncludedSections(String includedSections);

    /**
     * 설명/메모
     */
    @StringLength(StringLength.UNLIMITED)
    String getDescription();
    void setDescription(String description);

    /**
     * 생성자
     */
    String getCreatedBy();
    void setCreatedBy(String createdBy);

    /**
     * 생성 시각 (epoch ms)
     */
    @Indexed
    long getCreatedAt();
    void setCreatedAt(long createdAt);

    /**
     * 파일 크기 (bytes)
     */
    long getSizeBytes();
    void setSizeBytes(long sizeBytes);
}
