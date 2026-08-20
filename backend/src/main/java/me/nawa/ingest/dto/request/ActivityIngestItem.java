package me.nawa.ingest.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 한 항목의 활동 분류 전체입니다.
 *
 * <p>일부가 아니라 <b>전체</b>를 보냅니다. 목록에 없는 관계는 지워집니다.
 * 분류는 더해지기만 하는 값이 아니라 "지금 이 항목이 속한 분류"라서,
 * 빠진 것을 남겨 두면 지워진 분류가 영영 붙어 있게 됩니다.
 *
 * <p>적재 키는 pipelineId 입니다. activityId 는 시드된 분류 체계의 값을
 * 그대로 씁니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ActivityIngestItem {

    private String pipelineId;

    private List<ActivityLink> activities;

    /**
     * 분류 하나와 그것이 대표인지 여부입니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ActivityLink {

        private Long activityId;

        private Boolean isPrimary;
    }
}
