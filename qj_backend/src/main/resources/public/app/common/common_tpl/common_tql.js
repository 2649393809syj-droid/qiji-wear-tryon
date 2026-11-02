let search_user = `{{#each this}}<div class="row">
<div class="col">年龄:{{age}}</div>
<div class="col">姓名:{{name}}</div>
<div class="col">电话:{{tel}}</div>
<div class="col">推荐人:{{recommended}}</div>
<div class="col">已推荐:{{recommend}}</div>
<div class="col">
    <button class="btn btn-link check_btn" id="{{id}}" data-type="{{type}}">查看</button>
</div>
<div id="check_box_{{id}}" class="check_box">
    <table class="table table-secondary table-hover">
        <thead>
            <tr>
                <th>1</th>
                <th>2</th>
                <th>3</th>
                <th>4</th>
            </tr>
        </thead>
        <tbody>
            <tr>
                <td>对1</td>
                <td>对2</td>
                <td>对3</td>
                <td>对4</td>
            </tr>
        </tbody>
    </table>
</div>
</div>
{{/each}}`