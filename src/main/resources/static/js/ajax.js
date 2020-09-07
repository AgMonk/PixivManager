ajax = function(url,data,success){
    $.ajax({
        "url" : url,
        "data" : "f="+Math.random().toFixed(2)+"&"+data,
        "type" : "POST",
        "raditional" : true,
        "dataType" : "json",
        "success" : success
    })
}

newTd = function (t) {
    return $("<td></td>").text(t);
}
newOp = function (text, value) {
    value = value == null ? text : value;
    return $("<option></option>").text(text).val(value);
}
newButton = function (text,value,onclick) {
    return $("<button></button>").text(text).val(value).click(onclick);
}
newInput = function (name,value,clazz) {
    return $("<input>").attr("name",name).val(value).attr("class",clazz);
}
newSelect = function (name,clazz,array) {
    let s = $("<select></select>").attr("name",name).attr("class",clazz);

    array.forEach(function (currentValue, index) {

        s.append(newOp(currentValue,index))
    })


    return s;
}
newForm = function (id) {
    return $("<form></form>").attr("id",id);
}

