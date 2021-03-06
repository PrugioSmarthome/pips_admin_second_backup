package com.daewooenc.pips.admin.web.util.rabbitmq;

import com.daewooenc.pips.admin.web.common.WebConsts;
import com.daewooenc.pips.admin.web.domain.dto.housingcplx.HousingCplx;
import com.daewooenc.pips.admin.web.domain.vo.device.DeviceRegiVo;
import com.daewooenc.pips.admin.web.domain.vo.device.DeviceSyncVo;
import com.daewooenc.pips.admin.web.domain.vo.energy.EnergyEventDataVo;
import com.daewooenc.pips.admin.web.domain.vo.homenet.HousingCplxNotiResultVo;
import com.daewooenc.pips.admin.web.domain.vo.homenet.HousingCplxNotiVo;
import com.daewooenc.pips.admin.web.domain.vo.servermgmt.ServerConfCtrVo;
import com.daewooenc.pips.admin.web.domain.vo.servermgmt.ServerDataSendCtrVo;
import com.daewooenc.pips.admin.web.domain.vo.servermgmt.ServerMgmtVo;
import com.daewooenc.pips.admin.web.domain.vo.servermgmt.ServerStatusVo;
import com.daewooenc.pips.admin.web.service.api.ApiService;
import com.daewooenc.pips.admin.web.service.energy.EnergyService;
import com.daewooenc.pips.admin.web.service.homenet.HomenetService;
import com.daewooenc.pips.admin.web.service.housingcplx.HousingCplxService;
import com.daewooenc.pips.admin.web.service.servermgmt.ServerMgmtService;
import com.daewooenc.pips.admin.web.util.DateUtil;
import com.daewooenc.pips.admin.web.util.JsonUtil;
import com.daewooenc.pips.admin.web.util.XSSUtil;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;

/**
 * @author : yckim
 * @version :
 * @see <pre>
 * == Modification Information ==<br/>
 *
 *         Date        :       User          :     Description <br/>
 * ---------------------------------------------------------- <br/>
 *    2019-08-19       :       yckim        :                  <br/>
 *
 * </pre>
 * @since : 2019-08-19
 **/
@Component("myListen")
public class AmqpMessageListener implements MessageListener {
    /** ?????? ??????. */
    @SuppressWarnings("unused")
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ServerMgmtService serverMgmtService;

    @Autowired
    HousingCplxService housingCplxService;

    @Autowired
    EnergyService energyService;

    @Autowired
    private ApiService apiService;

    @Autowired
    HomenetService homenetService;

    @Autowired
    private XSSUtil xssUtil;

    /**
     *  amqp message listen
     * @param message
     */
    @SuppressWarnings("unchecked")
    @Override
    public void onMessage(Message message) {
        String receivedMessage = "";
        try {
            receivedMessage = new String(message.getBody(), StandardCharsets.UTF_8);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (!"".equals(receivedMessage) && receivedMessage != null) {
            String routeKey = message.getMessageProperties().getReceivedRoutingKey();

            if (routeKey != null && !"".equals(routeKey)) {
                if (routeKey.endsWith("cmd")) {
                    processControl(receivedMessage);
                } else if (routeKey.endsWith("event")) {
                    processEventData(receivedMessage);
                }
            }
        } else {
            logger.info("No Message");
        }
    }

    private void processControl(String receivedMessage) {
        String ctl_sts_cd = null;
        String ctl_tp_grp_cd = null;
        String ctl_tp_cd = null;
        JSONObject controlReqContent = null;
        JSONObject controlResContent = null;
        JSONObject jsonObject = new JSONObject(receivedMessage);
        String errorMessage = "";

        try {
            if (!jsonObject.isNull(WebConsts.CTL_TP_GRP_CD)) {
                ctl_tp_grp_cd = (String) jsonObject.get(WebConsts.CTL_TP_GRP_CD);

                // ????????? ?????? ?????? ?????? : ?????????/?????? ????????? ????????? ??????
                if (WebConsts.SYS_CTL_TP_CD.equalsIgnoreCase(ctl_tp_grp_cd)) {
                    if (!jsonObject.isNull(WebConsts.CTL_TP_CD)) {
                        ctl_tp_cd = (String) jsonObject.get(WebConsts.CTL_TP_CD);
                    }
                    if (!jsonObject.isNull(WebConsts.CTL_STS_CD)) {
                        ctl_sts_cd = (String) jsonObject.get(WebConsts.CTL_STS_CD);
                    }
                    if (!jsonObject.isNull(WebConsts.CTL_DEM_ORGNL_CONT)) {
                        controlReqContent = (JSONObject) jsonObject.get(WebConsts.CTL_DEM_ORGNL_CONT);
                    }

                    /**
                     * 1.6 ???????????? ?????? ?????? (=?????? ?????? ??????)??? ????????? ????????? ????????????.
                     * @link https://daewooenc.atlassian.net/wiki/spaces/PIPS/pages/36732985/1.6+-
                     */
                    if (WebConsts.DEVICE_CONF.equalsIgnoreCase(ctl_tp_cd) ) {
                        // ????????? ????????? ????????? ?????? ??????
                        ServerConfCtrVo serverCtrVo = JsonUtil.toObject(controlReqContent.toString(), ServerConfCtrVo.class);

                        String adminId = xssUtil.replaceAll(StringUtils.defaultString(serverCtrVo.getAdminId()));
                        adminId = StringUtils.defaultIfEmpty(adminId, "");

                        if (WebConsts.SUCCESS.equalsIgnoreCase(ctl_sts_cd)) {
                            try {
                                serverMgmtService.confControlUpdate(serverCtrVo);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        } else if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd) || WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd) ) {
                            if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "No Response Device.";
                            } else if (WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "Control Time Out.";
                            } else {
                                errorMessage = "Unknown Error.";
                            }

                            String userErrorMessage = getMessageForHmnetConf(serverCtrVo, adminId, errorMessage);

                            logger.info("===================errorMessage : "+errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        } else if (WebConsts.FAIL.equalsIgnoreCase(ctl_sts_cd)) {
                            if (!jsonObject.isNull(WebConsts.ADD_CONT)) {
                                errorMessage = (String) jsonObject.get(WebConsts.ADD_CONT);
                            }

                            String userErrorMessage = getMessageForHmnetConf(serverCtrVo, adminId, errorMessage);

                            logger.info("===================errorMessage : "+errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        }
                    }
                    /**
                     * 1.7 ????????? ?????? ?????? ?????? ?????? (=????????? ?????? ?????? ?????? ??????)??? ????????? ????????? ????????????.
                     * @link https://daewooenc.atlassian.net/wiki/spaces/PIPS/pages/36831272/1.7+-
                     */
                    else if (WebConsts.DATA_SEND.equalsIgnoreCase(ctl_tp_cd) ) {
                        ServerDataSendCtrVo serverDataSendCtrVo = JsonUtil.toObject(controlReqContent.toString(), ServerDataSendCtrVo.class);

                        String adminId = xssUtil.replaceAll(StringUtils.defaultString(serverDataSendCtrVo.getAdminId()));
                        String tgtTp = xssUtil.replaceAll(StringUtils.defaultString(serverDataSendCtrVo.getTgtTp()));

                        adminId = StringUtils.defaultIfEmpty(adminId, "");
                        tgtTp = StringUtils.defaultIfEmpty(tgtTp, "");

                        if (WebConsts.SUCCESS.equalsIgnoreCase(ctl_sts_cd)) {
                            try {
                                // ?????? ?????? ????????? ?????? ??????
                                if ("device".equalsIgnoreCase(tgtTp)) {
                                    boolean result = serverMgmtService.dataSendControlUpdateServerMgmt(serverDataSendCtrVo);
                                    // ?????? ?????? ??? service server ?????? ???????????? ??????
                                    if (result) {
                                        logger.debug("data send ?????? ?????? ????????? Service Server??? ??????(?????? ????????????)");
                                        String hmnetId = xssUtil.replaceAll(StringUtils.defaultString(serverDataSendCtrVo.getHmnetId()));
                                        String trnsmYn = xssUtil.replaceAll(StringUtils.defaultString(serverDataSendCtrVo.getTrnsmYn()));

                                        hmnetId = StringUtils.defaultIfEmpty(hmnetId, "");
                                        trnsmYn = StringUtils.defaultIfEmpty(trnsmYn, "");

                                        apiService.sendHmnetUseyn(adminId, hmnetId, trnsmYn);
                                    }
                                // ?????? ????????? ?????? ??????
                                } else if ("complex".equalsIgnoreCase(tgtTp)) {

                                    DateUtil.getDate(WebConsts.strDateFormat_yyyyMMddHHmmss_2, Calendar.getInstance().getTime());
                                    HousingCplx housingCplx = new HousingCplx();
                                    if ("Y".equalsIgnoreCase(serverDataSendCtrVo.getTrnsmYn())) {
                                        housingCplx.setDelYn("N");
                                    } else {
                                        housingCplx.setDelYn("Y");
                                    }
                                    housingCplx.setEditerId(adminId);
                                    housingCplx.setHouscplxCd(serverDataSendCtrVo.getHouscplxCd());
                                    boolean result = housingCplxService.updateHousingCplxDelyn(housingCplx);
                                    if (result) {
                                        logger.debug("Successfully updated useYN of the apartment complex : " + serverDataSendCtrVo.getTrnsmYn());
                                    } else {
                                        logger.info("Failed updated useYN of the apartment complex : " + serverDataSendCtrVo.getTrnsmYn());

                                        String userErrorMessage = getMessageForHmnetConfSend(serverDataSendCtrVo, adminId, tgtTp, "Failed updated useYN of the apartment complex");

                                        logger.info("===================user errorMessage : "+userErrorMessage);

                                        AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                                        amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                                    }
                                } else {
                                    logger.debug("target is not exists : "+ serverDataSendCtrVo.getTgtTp());

                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        } else if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd) || WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd) ) {
                            if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "No Response Device.";
                            } else if (WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "Control Time Out.";
                            } else {
                                errorMessage = "Unknown Error.";
                            }
                            String userErrorMessage = getMessageForHmnetConfSend(serverDataSendCtrVo, adminId, tgtTp, errorMessage);

                            logger.info("===================errorMessage : "+errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        } else if (WebConsts.FAIL.equalsIgnoreCase(ctl_sts_cd)) {
                            if (!jsonObject.isNull(WebConsts.ADD_CONT)) {
                                errorMessage = (String) jsonObject.get(WebConsts.ADD_CONT);
                            }
                            String userErrorMessage = getMessageForHmnetConfSend(serverDataSendCtrVo, adminId, tgtTp, errorMessage);

                            logger.info("===================errorMessage : "+errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        }
                        // ?????? ?????? ?????? ??????
                    } else if (WebConsts.NOTICE_INFO.equalsIgnoreCase(ctl_tp_cd) ) {
                        // ?????? ?????? ?????? ?????? ?????????
                        HousingCplxNotiVo housingCplxNotiVo = JsonUtil.toObject(controlReqContent.toString(), HousingCplxNotiVo.class);
                        // ?????? ?????? ?????? ?????? ?????????
                        if (!jsonObject.isNull(WebConsts.CTL_OUTCOM_CONT)) {
                            JSONArray controlResContentList = (JSONArray) jsonObject.get(WebConsts.CTL_OUTCOM_CONT);
//                            controlResContent = (JSONObject) jsonObject.get(WebConsts.CTL_OUTCOM_CONT);
                            List<HousingCplxNotiResultVo> housingCplxNotiResultVoList = JsonUtil.toObjectList(controlResContentList.toString(), HousingCplxNotiResultVo.class);
                            logger.debug("Noti Result : "+JsonUtil.toJson(housingCplxNotiResultVoList));
                        }
                    }
                    /**
                     * 1.7 ???????????? ????????? ????????? ????????? ????????? ????????????.
                     * @link https://daewooenc.atlassian.net/wiki/spaces/PIPS/pages/26607633/1.2+-
                     */
                    else if (WebConsts.ATTACHED_DEVICE_INFO.equalsIgnoreCase(ctl_tp_cd) ) {
                        DeviceSyncVo deviceSyncVo = JsonUtil.toObject(controlReqContent.toString(), DeviceSyncVo.class);

                        String adminId = xssUtil.replaceAll(StringUtils.defaultString(deviceSyncVo.getAdminId()));
                        adminId = StringUtils.defaultIfEmpty(adminId, "");

                        if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd) || WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd) ) {
                            if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "No Response Device.";
                            } else if (WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "Control Time Out.";
                            } else {
                                errorMessage = "Unknown Error.";
                            }
                            String userErrorMessage = getMessageForDeviceSync(deviceSyncVo, adminId, errorMessage);

                            logger.info("===================errorMessage : "+errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        } else if (WebConsts.FAIL.equalsIgnoreCase(ctl_sts_cd)) {
                            if (!jsonObject.isNull(WebConsts.ADD_CONT)) {
                                errorMessage = (String) jsonObject.get(WebConsts.ADD_CONT);
                            }
                            String userErrorMessage = getMessageForDeviceSync(deviceSyncVo, adminId, errorMessage);

                            logger.info("===================errorMessage : "+errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        }
                        //
                    }
                    /**
                     * 1.3 ?????? ?????? ?????? - ?????? ??? ?????? ?????? ??????(=?????? ??? ?????? ?????? ??????)??? ????????? ????????? ????????????.
                     * @link https://daewooenc.atlassian.net/wiki/spaces/PIPS/pages/28180484/1.3+-
                     */
                    else if (WebConsts.NODE_REGI.equalsIgnoreCase(ctl_tp_cd) ) {

                        DeviceRegiVo deviceRegiVo = JsonUtil.toObject(controlReqContent.toString(), DeviceRegiVo.class);

                        String adminId = xssUtil.replaceAll(StringUtils.defaultString(deviceRegiVo.getAdminId()));
                        adminId = StringUtils.defaultIfEmpty(adminId, "");

                        if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd) || WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd) ) {
                            if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "No Response Device.";
                            } else if (WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "Control Time Out.";
                            } else {
                                errorMessage = "Unknown Error.";
                            }
                            String userErrorMessage = getMessageForNodeRegiAndUserModify(deviceRegiVo, adminId, errorMessage);

                            logger.info("===================errorMessage : "+errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        } else if (WebConsts.FAIL.equalsIgnoreCase(ctl_sts_cd)) {
                            if (!jsonObject.isNull(WebConsts.ADD_CONT)) {
                                errorMessage = (String) jsonObject.get(WebConsts.ADD_CONT);
                            }
                            String userErrorMessage = getMessageForNodeRegiAndUserModify(deviceRegiVo, adminId, errorMessage);

                            logger.info("===================errorMessage : " + errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        } else if (WebConsts.SUCCESS.equalsIgnoreCase(ctl_sts_cd)) {
                                String userErrorMessage = getMessageForDeviceRegiSuccess();

                                AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                                amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                            }
                    }
                    /**
                     * 1.3 ?????? ????????? ?????? ????????? ????????? ????????? ????????????.
                     */
                    else if (WebConsts.USER_MODIFY.equalsIgnoreCase(ctl_tp_cd) ) {

                        DeviceRegiVo deviceRegiVo = JsonUtil.toObject(controlReqContent.toString(), DeviceRegiVo.class);

                        String adminId = xssUtil.replaceAll(StringUtils.defaultString(deviceRegiVo.getAdminId()));
                        adminId = StringUtils.defaultIfEmpty(adminId, "");

                        if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd) || WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd) ) {
                            if (WebConsts.D_NACK.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "No Response Device.";
                            } else if (WebConsts.TIME_OUT.equalsIgnoreCase(ctl_sts_cd)) {
                                errorMessage = "Control Time Out.";
                            } else {
                                errorMessage = "Unknown Error.";
                            }
                            String userErrorMessage = getMessageForNodeRegiAndUserModify(deviceRegiVo, adminId, errorMessage);

                            logger.info("===================errorMessage : "+errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        } else if (WebConsts.FAIL.equalsIgnoreCase(ctl_sts_cd)) {
                            if (!jsonObject.isNull(WebConsts.ADD_CONT)) {
                                errorMessage = (String) jsonObject.get(WebConsts.ADD_CONT);
                            }
                            String userErrorMessage = getMessageForNodeRegiAndUserModify(deviceRegiVo, adminId, errorMessage);

                            logger.info("===================errorMessage : " + errorMessage);

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        } else if (WebConsts.SUCCESS.equalsIgnoreCase(ctl_sts_cd)) {
                            String userErrorMessage = getMessageForDeviceRegiSuccess();

                            AmqpMessageProducer amqpMessageProducer = new AmqpMessageProducer();
                            amqpMessageProducer.sendMessage(adminId, userErrorMessage);
                        }
                    }
                } else {
                    logger.info("other control");
                }
            } else {
                logger.info("ctl_tp_grp_cd is not exists");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processEventData(String recvMessage) {
        JSONObject jsonObject = new JSONObject(recvMessage);

        String dat_tp_cd = null;
        JSONObject contJsonObject = null;
        String homenetID = null;
        try {
            if (!jsonObject.isNull(WebConsts.DAT_TP_CD)) {
                dat_tp_cd = (String) jsonObject.get(WebConsts.DAT_TP_CD);
            } else {
                logger.info("WebConsts.DAT_TP_CD is null");
            }
            if (!jsonObject.isNull(WebConsts.CONT)) {
                contJsonObject = (JSONObject) jsonObject.get(WebConsts.CONT);
            }
            if (contJsonObject != null) {
                if (dat_tp_cd != null && WebConsts.ENERGY_DATA.equalsIgnoreCase(dat_tp_cd)) {
                    logger.info("envergy data"+contJsonObject.toString());
                    EnergyEventDataVo energyEventDataVo = JsonUtil.toObject(contJsonObject.toString(), EnergyEventDataVo.class);
                    energyService.energyDataProcess(energyEventDataVo);
                } else if (dat_tp_cd != null && WebConsts.DEVICE_STATUS.equalsIgnoreCase(dat_tp_cd)) {
                    ServerMgmtVo serverMgmtVo = new ServerMgmtVo();
                    homenetID = (String) jsonObject.get("hmnet_id");
                    ServerStatusVo serverStatusVo = JsonUtil.toObject(contJsonObject.toString(), ServerStatusVo.class);
                    serverMgmtVo.setHmnetId(homenetID);
                    if ("available".equals(serverStatusVo.getStatus())) {
                        serverMgmtVo.setStsCd("OK");
                    } else {
                        serverMgmtVo.setStsCd("NOK");
                    }

                    serverMgmtService.statusUpdateServerMgmt(serverMgmtVo);
                }
            }
            else {
                logger.info("WebConsts.CONT is null");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     *  amqp message listen stop
     */
    @PreDestroy
    private void destroy() {
        logger.info("AmqpMessageListener destroyed");
    }

    /**
     * ???????????? ???????????? ?????? ??????????????? ??? ??????????????? ???????????? ?????? ????????? ??????
     * @param deviceSyncVo
     * @param adminId
     * @param errorMessage
     * @return
     */
    private String getMessageForDeviceSync(DeviceSyncVo deviceSyncVo, String adminId, String errorMessage) {
        String message = adminId + "?????? ????????????\n";
        String hmnetId = xssUtil.replaceAll(StringUtils.defaultString(deviceSyncVo.getHmnetId()));
        String houscplxCd = xssUtil.replaceAll(StringUtils.defaultString(deviceSyncVo.getHouscplxCd()));
        String dongNo = xssUtil.replaceAll(StringUtils.defaultString(deviceSyncVo.getDongNo()));
        String hoseNo = xssUtil.replaceAll(StringUtils.defaultString(deviceSyncVo.getHoseNo()));

        hmnetId = StringUtils.defaultIfEmpty(hmnetId, "");
        houscplxCd = StringUtils.defaultIfEmpty(houscplxCd, "");
        dongNo = StringUtils.defaultIfEmpty(dongNo, "");
        hoseNo = StringUtils.defaultIfEmpty(hoseNo, "");

        String houscplxNm = housingCplxService.selectHouscplxNm(houscplxCd);
        String hmnetNm = homenetService.getHomenetNm(hmnetId);

        //String target = houscplxCd + ", " + dongNo + ", " + hoseNo;
        String target = houscplxNm + ", " + dongNo + "???, " + hoseNo + "???";

        //message += hmnetId + "??? [" + target + "]??? ?????? ???????????? ????????? ?????? ??? ????????? ?????? ????????? ?????????????????????. " + errorMessage;
        message += hmnetNm + "??? [" + target + "]\n??? ?????? ???????????? ????????? ?????? ??? ????????? ?????? ????????? ?????????????????????.\n" + errorMessage;

        return message;
    }

    /**
     * ?????? ??? ?????? ?????? ????????? ?????? ??????????????? ??? ??????????????? ???????????? ?????? ????????? ??????
     * @param deviceRegiVo
     * @param adminId
     * @param errorMessage
     * @return
     */
    private String getMessageForDeviceRegi(DeviceRegiVo deviceRegiVo, String adminId, String errorMessage) {
        String message = adminId + "?????? ????????????\n";
        String userId = xssUtil.replaceAll(StringUtils.defaultString(deviceRegiVo.getUserId()));
        String hsholdId = xssUtil.replaceAll(StringUtils.defaultString(deviceRegiVo.getHsholdId()));

        userId = StringUtils.defaultIfEmpty(userId, "");
        hsholdId = StringUtils.defaultIfEmpty(hsholdId, "");

        String houscplxNm = housingCplxService.selectHouscplxNm(hsholdId.substring(0,6));
        String dongNo = hsholdId.substring(7,11);
        String hoseNo = hsholdId.substring(12);

        //String target = hsholdId + ", " + userId;
        String target = houscplxNm + ", " + dongNo + "???, " + hoseNo + "???, " + userId;

        message += "[" + target + "]\n??? ?????? ?????? ??? ?????? ?????? ?????? ?????? ??? ????????? ?????? ????????? ?????????????????????.\n" + errorMessage;

        return message;
    }

    private String getMessageForNodeRegiAndUserModify(DeviceRegiVo deviceRegiVo, String adminId, String errorMessage) {
        String message = adminId + "?????? ????????????\n";
        String userId = xssUtil.replaceAll(StringUtils.defaultString(deviceRegiVo.getUserId()));
        String hsholdId = xssUtil.replaceAll(StringUtils.defaultString(deviceRegiVo.getHsholdId()));

        userId = StringUtils.defaultIfEmpty(userId, "");
        hsholdId = StringUtils.defaultIfEmpty(hsholdId, "");

        String houscplxNm = housingCplxService.selectHouscplxNm(hsholdId.substring(0,6));
        String dongNo = hsholdId.substring(7,11);
        String hoseNo = hsholdId.substring(12);

        //String target = hsholdId + ", " + userId;
        String target = houscplxNm + ", " + dongNo + "???, " + hoseNo + "???, " + userId;

        message += "[" + target + "]\n??? ?????? ?????? ??? ?????? ?????? ?????? ?????? ??? ????????? ?????? ????????? ?????????????????????.\n" + errorMessage + "ND_UM";

        return message;
    }

    private String getMessageForDeviceRegiSuccess() {
        String message = "ND_UM";

        return message;
    }

    /**
     * ?????? ?????? ????????? ?????? ??????????????? ??? ??????????????? ???????????? ?????? ????????? ??????
     * @param serverConfCtrVo
     * @param adminId
     * @param errorMessage
     * @return
     */
    private String getMessageForHmnetConf(ServerConfCtrVo serverConfCtrVo, String adminId, String errorMessage) {
        String message = adminId + "?????? ????????????\n";
        String hmnetId = xssUtil.replaceAll(StringUtils.defaultString(serverConfCtrVo.getHmnetId()));
        hmnetId = StringUtils.defaultIfEmpty(hmnetId, "");

        String hmnetNm = homenetService.getHomenetNm(hmnetId);

        //message += hmnetId + "??? ?????? ?????? ?????? ?????? ??? ????????? ?????? ????????? ?????????????????????. " + errorMessage;
        message += hmnetNm + "??? ?????? ?????? ?????? ?????? ??? ????????? ?????? ????????? ?????????????????????.\n" + errorMessage;

        return message;
    }

    /**
     * ????????? ?????? ?????? ?????? ????????? ?????? ??????????????? ??? ??????????????? ???????????? ?????? ????????? ??????
     * @param serverDataSendCtrVo
     * @param adminId
     * @param tgtTp
     * @param errorMessage
     * @return
     */
    private String getMessageForHmnetConfSend(ServerDataSendCtrVo serverDataSendCtrVo, String adminId, String tgtTp, String errorMessage) {
        String message = adminId + "?????? ????????????\n";
        String trnsmYn = xssUtil.replaceAll(StringUtils.defaultString(serverDataSendCtrVo.getTrnsmYn()));
        trnsmYn = StringUtils.defaultIfEmpty(trnsmYn, "");

        if (trnsmYn.equals("Y")) {
            trnsmYn = "?????????";
        } else if (trnsmYn.equals("N")) {
            trnsmYn = "????????????";
        }

        if (tgtTp.equals("device")) {
            String hmnetId = xssUtil.replaceAll(StringUtils.defaultString(serverDataSendCtrVo.getHmnetId()));
            hmnetId = StringUtils.defaultIfEmpty(hmnetId, "");

            String hmnetNm = homenetService.getHomenetNm(hmnetId);

            //message += hmnetId + "??? " + trnsmYn + " ?????? ??? ????????? ?????? ????????? ?????????????????????. " + errorMessage;
            message += hmnetNm + "??? " + trnsmYn + " ?????? ??? ????????? ?????? ????????? ?????????????????????.\n" + errorMessage;

        } else if (tgtTp.equals("complex")) {
            String houscplxCd = xssUtil.replaceAll(StringUtils.defaultString(serverDataSendCtrVo.getHouscplxCd()));
            houscplxCd = StringUtils.defaultIfEmpty(houscplxCd, "");

            String houscplxNm = housingCplxService.selectHouscplxNm(houscplxCd);

            //message += houscplxCd + "??????????????? ?????? " + trnsmYn + " ?????? ??? ????????? ?????? ????????? ?????????????????????. " + errorMessage;
            message += houscplxNm + "????????? ?????? " + trnsmYn + " ?????? ??? ????????? ?????? ????????? ?????????????????????.\n" + errorMessage;
        }

        return message;
    }
}