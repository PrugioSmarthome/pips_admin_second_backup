<%@page contentType="text/html;charset=UTF-8"%>
<%@page pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib uri="http://www.springframework.org/tags/form" prefix="form"%>
<script type="text/javascript" charset="utf-8" src="<c:url value="/scripts/jquery/jquery.dataTables.min.js" />"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.11.0/umd/popper.min.js"></script>
<script src="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-beta/js/bootstrap.min.js"></script>
<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-beta/css/bootstrap.min.css">
<script type="text/javascript" charset="utf-8" src="<c:url value="/scripts/summernote.js" />"></script>
<link rel="stylesheet" type="text/css" href="<c:url value="/styles/summernote.css" />">
<link rel="stylesheet" type="text/css" href="<c:url value="/styles/jquery.dataTables.css" />">
<link rel="stylesheet" type="text/css" href="<c:url value="/styles/editor.css" />">

<style>
    .textarea {
      width: 100%;
      height: 300px;
      border: 1px solid red;
    }

    .note-toolbar{
        background-color:#ddd;
    }

    .dataTables_length {
            float: right !important;
    }
</style>
<script type="text/javascript">

    var danjiArray = new Array();
    var danjiArrayNm = new Array();

    $(document).on('change', 'input[name="box_chk"]', function(){
        if($(this).is(":checked")){
            var val = $(this).val().split("_");
            danjiArrayNm.push(val[1]);
            danjiArray.push(val[0]);
        } else{
            var val = $(this).val().split("_");
            for(let i = 0; i < danjiArrayNm.length; i++) {
              if(danjiArrayNm[i] == val[1]) {
                danjiArrayNm.splice(i, 1);
                i--;
              }
            }
            for(let i = 0; i < danjiArray.length; i++) {
              if(danjiArray[i] == val[0]) {
                danjiArray.splice(i, 1);
                i--;
              }
            }
        }
    });

    $(document).ready(function() {
        $("#btnHouscplx").hide();

        $('#selComplex').change(function() {
            if ($("#selComplex").val() == 'PART') {
                $("#btnHouscplx").show();
                $("#danjiList").show();
            }

            if ($("#selComplex").val() == 'ALL') {
                $("#btnHouscplx").hide();
                $("#danjiList").hide();
            }
        });

        $('#cont').summernote({
            height: 300,
            popover: {
            image: [],
            link: [],
            air: []
            }
        });

        $('#list_table').DataTable({
            "order": [],
            "bLengthChange": true,
            "lengthMenu": [10, 30, 50, 100],
            "pageLength" : 30,
            "dom": 'iltp',
            "language": {
                "info": "Total <span>_TOTAL_</span>???",
                "infoEmpty":"Total <span>0</span>???",
                "emptyTable": "????????? ???????????? ????????????.",
                "lengthMenu" : "_MENU_ ?????? ??????"
            },
            ajax:{
                "url":"/cm/common/housingcplx/listNotAll",
                "dataSrc":""
                },
                columns:[
                    {"data":"houscplxCd",
                            "render":function(data, type, row, meta){
                                var nm = row['houscplxNm'];
                                return "<div class='custom-control custom-checkbox'><input type='checkbox' name='box_chk' class='custom-control-input' id='"+data+"_"+nm+"' value='"+data+"_"+nm+"'><label class='custom-control-label' for='"+data+"_"+nm+"'></label></div>";
                            }
                    },
                    {"data":"houscplxNm",
                        "render":function(data, type, row, meta){
                            return row["houscplxNm"];
                        }
                    }
                ]
        });

        $("#list_table").css("width","100%");

    });

    function form_submit(flag){
        var cont = $('#cont').summernote('code');
        var RegExp = /[\,;:|*~`!^\-_+???<>@\#$%&\'\"\\\=]/gi;
        var str = $("#title").val();
        if(RegExp.test(str) == true){
            alert('??????????????? ????????? ??? ????????????.');
            return;
        }
        if(str == ""){
            alert('????????? ??????????????????.');
            return;
        }
        if($("#cont").val() == ""){
            alert('????????? ??????????????????.');
            return;
        }

        var isAttachFile = $("#isAttachFile").val();

        if (isAttachFile == "Y") {

            var fileSize = document.getElementById("inputGroupFile01").files[0].size;
            var maxSize = 500 * 1024 * 1024;

            if(fileSize > maxSize){
               alert("???????????? ???????????? 500MB ????????? ?????? ???????????????. ");
               return false;
            }
        }

        if ($("input:checkbox[name=mainPopupYn_]").is(":checked") == true) {
            $("#mainPopupYn").val("Y");
        }else {
            $("#mainPopupYn").val("N");
        }

        if($("#selComplex").val() == 'ALL'){
            $("#svcNotiTpCd").val("ALL");
            $("#danjiArray").val("");
        }else if($("#selComplex").val() == 'PART'){
            if($("#danjiArray").val() == null || $("#danjiArray").val() == ""){
                alert("????????? ??????????????????.");
                return;
            }
            $("#svcNotiTpCd").val("PART");
        }

        str = str.replace(/</g,"&lt;");
        str = str.replace(/>/g,"&gt;");
        $("#title").val(str);
        $("#tlrncYn").val(flag);
        $("#form_list").submit();
    }

    function parseMe(value) {
        return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    //???????????? ????????????
    function checkall(){
        var check = document.getElementsByName('box_chk');
        var checkall = document.getElementById("checkAll");

        if(checkall.checked){
            for(var i = 0 ; i < check.length ; i++){
                check[i].checked = true;
                var chechAllVal = check[i].value.split("_");
                danjiArrayNm.push(chechAllVal[1]);
                danjiArray.push(chechAllVal[0]);
            }
        }else{
            for(var i = 0 ; i < check.length ; i++){
                check[i].checked = false;
                var chechAllVal = check[i].value.split("_");

                for(let j = 0; j < danjiArray.length; j++) {
                    if(danjiArray[j] == chechAllVal[0]) {
                        danjiArray.splice(j, 1);
                        j--;
                    }
                }
                for(let k = 0; k < danjiArrayNm.length; k++) {
                    if(danjiArrayNm[k] == chechAllVal[1]) {
                        danjiArrayNm.splice(k, 1);
                        k--;
                    }
                }
            }
        }
    }

    //????????????
    function danjiCheck(){

        if(danjiArrayNm[0] == null){
            alert("????????? ??????????????????.");
            return;
        }

        var result = "<table id='danjiListTable' class='table' style='text-align:center;'><thead><tr><th style='text-align:center;'>?????????</th></tr></thead><tbody>";
        for(var i=0;i<danjiArrayNm.length;i++){
            result += "<tr><td style='text-align:center;'>"+danjiArrayNm[i]+"</td></tr>";
        }
        result += "</tbody></table>";

        $("#danjiList").empty();
        $("#danjiList").append(result);

        $("#danjiArray").val(danjiArray);

        $("#modal1").modal('hide');
    }

    //?????? ??????????????? ??????
    function list_search(){
        var text = $("#search_text").val();
        $('#list_table').DataTable().search(text).draw();
    }

    function back(){
        window.history.back();
    }

</script>
<form:form id="form_list" action="/cm/system/notice/addNoticeAction" name="community" commandName="community" method="post" enctype="multipart/form-data">
<div id="container">
    <div class="container">
        <div class="top_area">
            <h2 class="tit">????????? ???????????? ????????????</h2>
            <ul class="location">
                <li>????????? ??????</li>
                <li>????????? ????????????</li>
                <li>????????? ???????????? ????????????</li>
            </ul>
        </div>

        <div class="table_wrap2">
            <table class="table2">
                <colgroup>
                    <col style="width:150px"/>
                    <col style="width:30%"/>
                    <col style="width:150px"/>
                    <col style="width:80px"/>
                    <col />
                </colgroup>
                <tbody>

                    <tr>
                        <th>??????</th>
                        <td colspan="4"><input type="text" class="form-control" id="title" name="title" maxlength="100"/></td>
                    </tr>
                    <tr>
                        <th>????????????</th>
                        <td>
                            <select name="selComplex" id="selComplex" class="custom-select">
                                <option value="ALL" selected>??????</option>
                                <option value="PART">????????????</option>
                            </select>
                        </td>
                        <th>??????????????????</th>
                        <td>
                        <div class="custom-control custom-checkbox" style="vertical-align: middle">
                            <input type="checkbox" class="custom-control-input" id="mainPopupYn_" name="mainPopupYn_" />
                            <label class="custom-control-label" for="mainPopupYn_"></label>
                        </div>
                        </td>
                        <td>
                        <div id="btnHouscplx">
                            <button class="btn btn-gray btn-sm" type="button" data-toggle="modal" data-target="#modal1">????????????</button>
                        </div>
                        </td>
                    </tr>
                    <tr>
                        <th>????????????</th>
                        <td colspan="4"><textarea name="cont" id="cont"></textarea></td>
                    </tr>
                    <tr>
                        <th>????????????</th>
                        <td colspan="4">
                            <div class="custom-file">
                                <input type="file" class="custom-file-input" id="inputGroupFile01" name="inputGroupFile01" aria-describedby="inputGroupFileAddon01" value="<c:out value="${community.file}"/>" accept=".png, .jpg, .jpeg, .gif, .doc, .docx, .xls, .xlsx, .hwp, .ppt, .pptx, .txt"/>
                                <label class="custom-file-label" for="inputGroupFile01">Choose file</label>
                            </div>
                            <script type="text/javascript">
                                $('#inputGroupFile01').on('change',function(){
                                    var fileName = $(this).val();
                                    $(this).next('.custom-file-label').html(fileName);
                                    $("#isAttachFile").val("Y");
                                })
                            </script>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>

        <br/>
        <div id="danjiList" style="width: 100%; height:auto; max-height:300px; overflow-y:auto; overflow-x: hidden;">
        </div>

        <div class="tbl_btm_area2">
            <div class="right_area">
                <button class="btn btn-gray" type="button" type="button" onclick="back();">??????</button>
                <button class="btn btn-gray" type="button" onclick="form_submit('N');">????????????</button>
                <button class="btn btn-bluegreen" type="button" onclick="form_submit('Y');">????????????</button>
            </div>
        </div>
        <input type="text" name="tlrncYn" id="tlrncYn" style="width:0;height:0;visibility:hidden"/>
        <input type="text" name="isAttachFile" id="isAttachFile" style="width:0;height:0;visibility:hidden"/>
        <input type="text" name="svcNotiTpCd" id="svcNotiTpCd" style="width:0;height:0;visibility:hidden"/>
        <input type="text" name="mainPopupYn" id="mainPopupYn" style="width:0;height:0;visibility:hidden"/>
        <input type="text" name="danjiArray" id="danjiArray" style="width:0;height:0;visibility:hidden"/>
        <input type="text" name="danjiArrayNm" id="danjiArrayNm" style="width:0;height:0;visibility:hidden"/>
    </div>
</div>
</form:form>

<!-- ???????????? ?????? -->
<div class="modal fade" id="modal1" tabindex="-1" role="dialog">
    <div class="modal-dialog modal-dialog-centered " role="document">
        <div class="modal-content">
            <!-- ???????????? -->
            <div class="modal-header">
                <h5 class="modal-title">????????????</h5>
                <button type="button" id="closebtn" class="close" data-dismiss="modal"><img src="/images/btn_x.png" alt="" /></button>
            </div>
            <!-- //???????????? -->

            <!-- ???????????? -->
            <div class="modal-body">
            <div class="container">
            <div class="row">
            <div class="col-lg-12 col-sm-12 col-11 main-section">
                <!-- ???????????? -->
                <div class="search_area">
                    <table>
                        <colgroup>
                            <col style="width:70px"/>
                            <col />
                            <col style="width:95px"/>
                        </colgroup>
                        <tbody>
                            <tr>
                                <th>?????????</th>
                                <td><input type="text" class="form-control" id="search_text"/></td>
                                <td class="text-right"><button type="button" id="search_btn" onclick="list_search();" class="btn btn-brown btn-sm">??????</button></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <!-- //???????????? -->

                <!-- ??????????????? -->
                <div class="tbl_top_info mt-4">
                    <div class="total"></div>
                </div>
                <!-- //??????????????? -->

                <!-- ????????? -->
                <div style="width: 100%; height:auto; max-height:300px; overflow-y:auto; overflow-x: hidden;" class="table_wrap mt-3">
                    <table class="table" id="list_table" style="text-align:center;">
                        <thead>
                            <tr>
                                <th scope="col" style="text-align:center;">
                                    <div class="custom-control custom-checkbox">
                                        <input type="checkbox" class="custom-control-input" id="checkAll" onclick="javascript:checkall();">
                                        <label class="custom-control-label" for="checkAll"></label>
                                    </div>
                                </th>
                                <th scope="col" style="text-align:center;">??????</th>
                            </tr>
                        </thead>
                        <tbody id="householdDeviceList">
                        </tbody>
                    </table>
                </div>
                <!-- //????????? -->
            </div>
            </div>
            </div>
            </div>
            <!-- //???????????? -->
        <button class="btn btn-gray" type="button" onclick="javascript:danjiCheck()">????????????</button>
        </div>
    </div>
</div>