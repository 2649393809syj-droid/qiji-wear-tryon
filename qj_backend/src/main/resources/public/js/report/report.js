$(function(){
// 获取url拼接参数值
    function GetRequest() {
        var url = location.search; // 获取url中"?"符后的字串
        var theRequest = new Object();
        // console.log(url);
        // $("#answer").append(url)
        if (url.indexOf("?") != -1) {
            var str = url.substr(1);
            strs = str.split("&");
            console.log(str);
            // alert(strs)
            for (var i = 0; i < strs.length; i++) {
                theRequest[strs[i].split("=")[0]] = unescape(strs[i].split("=")[1]);
            }
        }
        return theRequest;
    }

    console.log(GetRequest())
    let openid = GetRequest().openid



    console.log(openid)
    
    // 使用新的后端接口获取用户报告数据
    var option = {
        url:'/qj/callfunc',
        type:"POST",
        data: JSON.stringify({
            openid: openid
        }),
        headers: {
            'Content-Type': 'application/json',
            'X-Authorization': 'test-jwt-token',
            'X-Function-Name': 'F_401_5008_REPORT_GET_USER_INFO',
            'X-Action': 'customize',
            'X-Mysql-Excludes': '',
            'X-Module-Name': 'qj_report',
            'X-Function-Code': '1125008'
        },
        success: function(response) {
            console.log("API响应:", response);
            let reportData = response;
            console.log("解析后的数据:", reportData);
            
            // 检查数据是否存在
            if (!reportData || !reportData.result) {
                console.error("API返回数据格式错误:", reportData);
                return;
            }
            
            // 模拟原来的数据结构
            let skin = {
                data: {
                    shape: reportData.result.shape,
                    types: reportData.result.types,
                    skin_color_obj: reportData.result.skin_color_obj
                }
            }
            
            let user = {
                data: {
                    nickName: reportData.result.nick_name
                }
            }
            
            console.log("用户数据:", user);
            console.log("皮肤数据:", skin);
            
            // 渲染用户名称
            if (user.data.nickName === "微信用户"){
                $("#nickName").html("客户");
            }else{
                $("#nickName").html(user.data.nickName);
            }
            
            // 渲染体型信息
            if (skin.data.shape) {
                $("#shape").html(skin.data.shape);
            }
            
            // 渲染体型图片
            renderShapeImage(skin.data.shape);
            
            // 渲染类型信息
            if (skin.data.types && skin.data.types.length > 0) {
                renderTypes(skin.data.types);
            }
            
            // 渲染肤色信息
            if (skin.data.skin_color_obj) {
                renderSkinColor(skin.data.skin_color_obj);
            }
        },
        error: function(xhr, status, error) {
            console.error("API调用失败:", error);
            console.error("响应文本:", xhr.responseText);
        }
    }

    $.ajax(option);
    
    // 渲染函数
    function renderShapeImage(shape) {
        if (shape === "倒三角型"){
        $("#invertedTriangle").show()
        $("#straight").remove()
        $("#equilibrium").remove()
        $("#pear").remove()
        $("#round").remove()
        $("#hourglass").remove()
        }else if(shape === "直线型"){
        $("#invertedTriangle").remove()
        $("#straight").show()
        $("#equilibrium").remove()
        $("#pear").remove()
        $("#round").remove()
        $("#hourglass").remove()
        }else if (shape === "均衡型"){
        $("#invertedTriangle").remove()
        $("#straight").remove()
        $("#equilibrium").show()
        $("#pear").remove()
        $("#round").remove()
        $("#hourglass").remove()
        }else if (shape === "梨型"){
        $("#invertedTriangle").remove()
        $("#straight").remove()
        $("#equilibrium").remove()
        $("#pear").show()
        $("#round").remove()
        $("#hourglass").remove()
        }else if (shape === "圆型"){
        $("#invertedTriangle").remove()
        $("#straight").remove()
        $("#equilibrium").remove()
        $("#pear").remove()
        $("#round").show()
        $("#hourglass").remove()
        }else if (shape === "沙漏型"){
        $("#invertedTriangle").remove()
        $("#straight").remove()
        $("#equilibrium").remove()
        $("#pear").remove()
        $("#round").remove()
        $("#hourglass").show()
    }
    }
    
    function renderTypes(types) {
        // 隐藏所有类型
    $("#roman").hide()
    $("#exotic").hide()
    $("#drama").hide()
    $("#girl").hide()
    $("#juvenile").hide()
    $("#vanguard").hide()
    $("#romance").hide()
    $("#nature").hide()
    $("#elegance").hide()
    $("#classical").hide()

        // 显示对应的类型
        for(i = 0; i < types.length; i++){
            $('#style').append(types[i].type, types[i].typeProportion)
            if (types[i].type === "古典"){
            $("#classical").show()
            }else if(types[i].type === "优雅"){
            $("#elegance").show()
            }else if (types[i].type === "自然"){
            $("#nature").show()
            }else if (types[i].type === "浪漫"){
            $("#romance").show()
            }else if (types[i].type === "前卫"){
            $("#vanguard").show()
            }else if (types[i].type === "少年"){
            $("#juvenile").show()
            }else if (types[i].type === "少女"){
            $("#girl").show()
            }else if (types[i].type === "戏剧"){
            $("#drama").show()
            }else if (types[i].type === "异域"){
            $("#exotic").show()
            }else if (types[i].type === "罗曼"){
            $("#roman").show()
        }
            if (i == types.length-1){
            $('#style').append('。')
        }else {
            $('#style').append('，')
        }
        }
    }

    function renderSkinColor(skinColorObj) {
        if (skinColorObj.tone && skinColorObj.vividness && skinColorObj.colorDegree && skinColorObj.lusterDegree) {
            $("#skinColor").append(skinColorObj.tone+"、"+skinColorObj.vividness+"、"+skinColorObj.colorDegree+"、"+skinColorObj.lusterDegree+"，您属于"+skinColorObj.typeBeauty+"。")
        }
    }
    
    // 暂时使用空的内容数据，或者你可以创建相应的接口
    let content = {
        data: []
    }
    console.log("content:",content)
    
    // 注意：这里的内容渲染逻辑暂时被注释掉，因为content.data是空的
    // 如果需要渲染内容，需要创建相应的后端接口来提供content数据

    // var body = document.getElementById("iftest").contentDocument.body;
    // var head = document.getElementById("iftest").contentDocument.head;
    // var page = $('html').html()
    // var body = $('body').html()
    // console.log($('html').html())
    // //拼接成html
    // var htmlStr = "<html>" + page + "</html>";
    // body.split('<script>')
    // console.log(body)
    // localStorage.setItem("body",body)
})