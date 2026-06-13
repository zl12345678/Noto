package com.noto.zhihui.scheduler;

import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.service.AiDigestService;
import com.noto.zhihui.service.AiWeeklyRetroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
public class AiDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiDigestScheduler.class);

    private final AiDigestService aiDigestService;
    private final AiWeeklyRetroService aiWeeklyRetroService;
    private final NotoAiProperties aiProperties;

    public AiDigestScheduler(
            AiDigestService aiDigestService,
            AiWeeklyRetroService aiWeeklyRetroService,
            NotoAiProperties aiProperties
    ) {
        this.aiDigestService = aiDigestService;
        this.aiWeeklyRetroService = aiWeeklyRetroService;
        this.aiProperties = aiProperties;
    }

    /** 每小时整点检查是否有用户需要生成每日 digest */
    @Scheduled(cron = "0 0 * * * *")
    public void runHourlyDigestCheck() {
        if (!aiProperties.isEnabled()) {
            return;
        }
        int hour = LocalTime.now().getHour();
        log.debug("Running daily digest check for hour {}", hour);
        aiDigestService.runScheduledDigests(hour);
        if (LocalDate.now().getDayOfWeek() == DayOfWeek.FRIDAY) {
            log.debug("Running weekly retro check for hour {}", hour);
            aiWeeklyRetroService.runScheduledWeeklyRetros(hour);
        }
    }
}
