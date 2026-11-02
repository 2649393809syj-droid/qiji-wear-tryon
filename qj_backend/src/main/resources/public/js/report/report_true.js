$(function (){
    // $('#v').load("report.html")

    var option = {
        url:'/v2/repot/genInWin',
        type:"post",
        data:{
            htmlFilePath:'E:\\shiyanshi\\qjapi-0529\\qjapi\\src\\main\\resources\\public\\pages\\report\\report.html',
            pdfFilePath:'E:\\shiyanshi\\qjapi-0529\\qjapi\\src\\main\\resources\\public\\pages\\report\\report.pdf'
        },
        async:false

    }
    var res = $.ajax(option)
    let user = JSON.parse(res.responseText)
})