$(function (){
    var data = [
        {
            id:1,
            age : 18,
            name : "无鞋",
            tel:114514,
            type:"hide",
            recommended:"",
            recommend:"光脚"
        },
        {
            id:2,
            age : 13,
            name : "无邪",
            tel:856423,
            type:"hide",
            recommended:"不败",
            recommend:""
        },
        {
            id:3,
            age : 45,
            name : "白眉",
            tel:54795623,
            type:"hide",
            recommended:"萨拉",
            recommend:"金角"
        }
    ]

    var template = Handlebars.compile(search_user)
    $("#user_msg_box").html(template(data))

    $(".check_box").hide()

    $(".check_btn").click(function (){
        console.log($(this).data("type"));
        let check_box_type = $(this).data("type");
        console.log($(this).prop("id"));
        let check_btn_id = $(this).prop("id");
        if (check_box_type == "hide"){
            $(this).data("type","show")
            $("#check_box_"+check_btn_id).show()
        }
        if (check_box_type == "show"){
            $(this).data("type","hide")
            $("#check_box_"+check_btn_id).hide()
        }
    })


})