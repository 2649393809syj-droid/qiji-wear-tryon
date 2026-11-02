$(function (){
    $("#upload_btn").click(function (){
        $("input").each(function (){
            if (this.value){
                console.log(this.value)
            }else{
                console.log(this.placeholder)
            }

        })
    })
})