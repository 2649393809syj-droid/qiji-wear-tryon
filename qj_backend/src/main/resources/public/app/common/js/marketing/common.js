$(function (){
    $("#show_all").click(function (){
        $(".check_box").show();
        $(".check_btn").data("type","show")
    })

    $("#hide_all").click(function (){
        $(".check_box").hide();
        $(".check_btn").data("type","hide")
    })

})