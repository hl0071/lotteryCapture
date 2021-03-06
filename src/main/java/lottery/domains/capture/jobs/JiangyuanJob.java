package lottery.domains.capture.jobs;

import com.alibaba.fastjson.JSON;
import javautils.date.Moment;
import javautils.http.HttpClientUtil;
import lottery.domains.capture.sites.jiangyuan365.JiangYuan365Bean;
import lottery.domains.capture.utils.CodeValidate;
import lottery.domains.capture.utils.ExpectValidate;
import lottery.domains.content.biz.LotteryOpenCodeService;
import lottery.domains.content.entity.LotteryOpenCode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 奖源365，主要用来获取腾讯分分彩数据，但当前期不获取，当前期以PCQQJob为准
 * 在http://www.jiangyuan365.com注册账号后获取token
 * http://www.jiangyuan365.com/Issue/history?lottername=AHK3
 */
@Component
public class JiangyuanJob {
    private static final Logger logger = LoggerFactory.getLogger(JiangyuanJob.class);
    private static boolean isRuning = false;
    private static final String TOKEN = "K25d259ec70da5f"; // token，注册后有 setyourtokenhere替换为自己的token 在
    private static final String URL = "http://free.jiangyuan365.com/" + TOKEN + "/%s-%s.json";

    // 彩票code列表
    private static final Map<String, String> LOTTERIES = new HashMap<>();

    static {
        // key：对方code，value：我方code
//		LOTTERIES.put("txffc", "txffc"); // 腾讯分分彩
		LOTTERIES.put("GD11X5", "gd11x5");
		LOTTERIES.put("JX11X5", "jx11x5");
		LOTTERIES.put("AF11X5", "ah11x5");
		LOTTERIES.put("SH11X5", "sh11x5");
		LOTTERIES.put("SD11X5", "sd11x5");
//
//
        LOTTERIES.put("TJSSC", "tjssc");//天津时时彩
        LOTTERIES.put("BJKL8", "bjkl8");//北京快乐8

		LOTTERIES.put("AHK3", "ahk3");//安徽快3
		LOTTERIES.put("HBK3", "hbk3"); //湖北快3
		LOTTERIES.put("JSKS", "jsk3"); //江苏快三
		LOTTERIES.put("GXKS", "gxk3"); // 广西快3
//		LOTTERIES.put("shk3", "shk3");
//		LOTTERIES.put("bjk3", "bjk3");
//		LOTTERIES.put("hebk3", "hebk3");
//		LOTTERIES.put("gsk3", "gsk3");
//		LOTTERIES.put("jxk3", "jxk3");
    }


    @Autowired
    private LotteryOpenCodeService lotteryOpenCodeService;


    @Scheduled(cron = "0/20 * * * * *") // 注意频率，每次间隔大于1秒
//	@Scheduled(cron = "8,15,20,25,30,35 * * * * *")
    // @PostConstruct
    public void execute() {
        synchronized (JiangyuanJob.class) {
            if (isRuning == true) {
                return;
            }
            isRuning = true;
        }

        try {
            logger.debug("开始抓取奖源365腾讯分分彩开奖数据>>>>>>>>>>>>>>>>");

            long start = System.currentTimeMillis();
            start();
            long spend = System.currentTimeMillis() - start;

            logger.debug("完成抓取奖源365腾讯分分彩开奖数据>>>>>>>>>>>>>>>>耗时{}", spend);
        } catch (Exception e) {
            logger.error("抓取奖源365腾讯分分彩开奖数据出错", e);
        } finally {
            isRuning = false;
        }
    }

    private void start() {
        for (String lottery : LOTTERIES.keySet()) {
            try {
                String realName = LOTTERIES.get(lottery);

                String result = getResult(lottery, 10);

                handleData(realName, result);
            } catch (Exception e) {
                logger.error("抓取奖源365腾讯分分彩" + lottery + "开奖数据出错", e);
            }
        }
    }

    public String getResult(String name, int num) {
        String url = String.format(URL, name, num) + "?_=" + System.currentTimeMillis();
        String result = get(url);
        return result;
    }

    @SuppressWarnings("rawtypes")
    private void handleData(String realName, String result) {
        if (StringUtils.isEmpty(result)) {
            return;
        }

        List<JiangYuan365Bean> openCodes = JSON.parseArray(result, JiangYuan365Bean.class);
        if (CollectionUtils.isEmpty(openCodes)) {
            return;
        }

        // 按期号升序
        Collections.sort(openCodes, new Comparator<JiangYuan365Bean>() {
            @Override
            public int compare(JiangYuan365Bean o1, JiangYuan365Bean o2) {
                if (o1.getIssue().compareTo(o2.getIssue()) > 0) {
                    return 1;
                } else if (o1.getIssue().compareTo(o2.getIssue()) < 0) {
                    return -1;
                }

                return 0;
            }
        });

        if ("txffc".equals(realName)) {
            // 腾讯分分彩移掉最新的2期
            if (openCodes.size() <= 2) {
                return;
            }

            openCodes.remove(openCodes.size() - 1);
            openCodes.remove(openCodes.size() - 2);
        }

        if (CollectionUtils.isEmpty(openCodes)) {
            return;
        }


        // 处理数据
        for (JiangYuan365Bean openCode : openCodes) {
            handleBean(realName, openCode);
        }
    }

    private boolean handleBean(String realName, JiangYuan365Bean openCode) {
        LotteryOpenCode lotteryOpenCode = new LotteryOpenCode();
        lotteryOpenCode.setInterfaceTime(openCode.getOpendate());
        lotteryOpenCode.setLottery(realName);
        lotteryOpenCode.setTime(new Moment().toSimpleTime());
        lotteryOpenCode.setOpenStatus(0);
        lotteryOpenCode.setRemarks("JiangYuan365");

        switch (realName) {
            // 腾讯分分彩
            case "txffc":
                lotteryOpenCode.setCode(openCode.getCode());
                lotteryOpenCode.setExpect(openCode.getIssue());
                break;
            case "gd11x5":
            case "sd11x5":
            case "ah11x5":
            case "jsk3":
                lotteryOpenCode.setCode(openCode.getCode());
                lotteryOpenCode.setExpect("20" + openCode.getIssue().substring(0, 6) + "-" + openCode.getIssue().substring(openCode.getIssue().length() - 3, openCode.getIssue().length()));
                break;
            case "sh11x5":
            case "ahk3":
            case "gxk3":
            case "tjssc":
                lotteryOpenCode.setCode(openCode.getCode());
                String expect = openCode.getIssue();
                String num = expect.substring(expect.length() - 2, expect.length());
                lotteryOpenCode.setExpect(expect.substring(0, 8) + "-" + String.format("%03d", Integer.valueOf(num)));
                break;
            case "hbk3":
                lotteryOpenCode.setCode(openCode.getCode());
                String hbk3 = openCode.getIssue();
                String temp = hbk3.substring(hbk3.length() - 3, hbk3.length());
                lotteryOpenCode.setExpect(hbk3.substring(0, 8) + "-" + String.format("%03d", Integer.valueOf(temp)));
                break;
            default:
                lotteryOpenCode.setCode(openCode.getCode());
                lotteryOpenCode.setExpect(openCode.getIssue());
                break;
        }

        if (StringUtils.isEmpty(lotteryOpenCode.getCode()) || StringUtils.isEmpty(lotteryOpenCode.getExpect())) {
            return false;
        }

		/*if (CodeValidate.validate(realName, lotteryOpenCode.getCode()) == false) {
			logger.error("奖源365腾讯分分彩" + realName + "抓取号码" + lotteryOpenCode.getCode() + "错误");
			return false;
		}

		if (ExpectValidate.validate(realName, lotteryOpenCode.getExpect()) == false) {
			logger.error("奖源365腾讯分分彩" + realName + "抓取期数" + lotteryOpenCode.getExpect() + "错误");
			return false;
		}*/

        boolean added = lotteryOpenCodeService.add(lotteryOpenCode, true);
        if (added) {
            // 腾讯龙虎斗
            if ("txffc".equals(realName)) {
                LotteryOpenCode txlhdCode = new LotteryOpenCode("txlhd", lotteryOpenCode.getExpect(), lotteryOpenCode.getCode(), lotteryOpenCode.getTime(), lotteryOpenCode.getOpenStatus(), null, lotteryOpenCode.getRemarks());
                txlhdCode.setInterfaceTime(lotteryOpenCode.getInterfaceTime());
                lotteryOpenCodeService.add(txlhdCode, false);
            }
        }

        return added;
    }

    public static String get(String urlAll) {
        try {
            String result = HttpClientUtil.get(urlAll, null, 5000);
            return result;
        } catch (Exception e) {
            logger.error("请求奖源365腾讯分分彩出错", e);
            return null;
        }
    }
}