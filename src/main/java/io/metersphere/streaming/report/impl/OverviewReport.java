package io.metersphere.streaming.report.impl;

import io.metersphere.streaming.commons.constants.ReportKeys;
import io.metersphere.streaming.report.base.ChartsData;
import io.metersphere.streaming.report.base.Statistics;
import io.metersphere.streaming.report.base.TestOverview;
import io.metersphere.streaming.report.graph.consumer.DistributedActiveThreadsGraphConsumer;
import io.metersphere.streaming.report.parse.ResultDataParse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.jmeter.report.processor.*;
import org.apache.jmeter.report.processor.graph.impl.HitsPerSecondGraphConsumer;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class OverviewReport extends AbstractReport {

    @Override
    public String getReportKey() {
        return ReportKeys.Overview.name();
    }

    private final DecimalFormat responseTimeFormat = new DecimalFormat("0.0000");

    @Override
    public void execute() {
        TestOverview testOverview = new TestOverview();

        SampleContext activeDataMap = sampleContextMap.get(DistributedActiveThreadsGraphConsumer.class.getSimpleName());
        List<ChartsData> usersList = ResultDataParse.graphMapParsing(activeDataMap.getData(), "users", "yAxis");
        Map<String, List<ChartsData>> collect = usersList.stream().collect(Collectors.groupingBy(ChartsData::getGroupName));
        AtomicInteger maxUser = new AtomicInteger();
        collect.forEach((k, cs) -> {
            Optional<ChartsData> max = cs.stream().max(Comparator.comparing(ChartsData::getyAxis));
            int i = max.get().getyAxis().setScale(0, BigDecimal.ROUND_UP).intValue();
            maxUser.addAndGet(i);
        });

        SampleContext hitsDataMap = sampleContextMap.get(HitsPerSecondGraphConsumer.class.getSimpleName());
        List<ChartsData> hitsList = ResultDataParse.graphMapParsing(hitsDataMap.getData(), "hits", "yAxis2");
        double hits = hitsList.stream().map(ChartsData::getyAxis2)
                .mapToDouble(BigDecimal::doubleValue)
                .average().orElse(0);

        SampleContext errorDataMap = sampleContextMap.get(StatisticsSummaryConsumer.class.getSimpleName());
        List<Statistics> statisticsList = ResultDataParse.summaryMapParsing(errorDataMap.getData(), Statistics.class);

        if (CollectionUtils.isNotEmpty(statisticsList)) {
            int size = statisticsList.size();
            Statistics statistics = statisticsList.get(size - 1);
            if ("[res_key=reportgenerator_summary_total]".equals(statistics.getLabel())) {
                String transactions = statistics.getTransactions();
                String tp90 = statistics.getTp90();
                String responseTime = statistics.getAverage();
                String avgBandwidth = statistics.getReceived();
                //
                testOverview.setAvgTransactions(decimalFormat.format(Double.parseDouble(transactions)));
                testOverview.setAvgResponseTime(responseTimeFormat.format(Double.parseDouble(responseTime) / 1000));
                testOverview.setResponseTime90(responseTimeFormat.format(Double.parseDouble(tp90) / 1000));
                testOverview.setAvgBandwidth(decimalFormat.format(Double.parseDouble(avgBandwidth)));
            }
        }


        SampleContext sampleDataMap = sampleContextMap.get(RequestsSummaryConsumer.class.getSimpleName());
        Map<String, Object> data = sampleDataMap.getData();
        String error = "";
        for (String key : data.keySet()) {
            MapResultData mapResultData = (MapResultData) data.get(key);
            ResultData koPercent = mapResultData.getResult("KoPercent");
            error = ((ValueResultData) koPercent).getValue().toString();
        }

        testOverview.setMaxUsers(String.valueOf(maxUser.get()));
        testOverview.setAvgThroughput(decimalFormat.format(hits));

        testOverview.setErrors(decimalFormat.format(Double.valueOf(error)));

        saveResult(reportId, testOverview);
    }
}
