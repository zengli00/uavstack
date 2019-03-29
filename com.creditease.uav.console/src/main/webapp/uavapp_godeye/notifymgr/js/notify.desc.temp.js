/**
 * 
 * @param appendId  ：window.winmgr.build对象ID
 * @param datas
 */
function loadDescDiv(appendId,datas){

	/***
	* 因为是同一预警，为了拿到首次预警时间故获取最后一个输出
	*/
	var mainObj = datas[datas.length-1];
	var ntfkey = mainObj["ntfkey"];
	var time = TimeHelper.getTime(mainObj["time"],'FMS');
	/**
	 * init head
	 */
	var sb=new StringBuffer();
	sb.append("<div class=\"title-head\">");
	sb.append("<span>预警详情</span>");
	if(mainObj['state']==25) {//已处理状态则按钮不可用
		sb.append("&nbsp;&nbsp;<button type=\"button\" disabled=\"disabled\" class=\"btn btn-SetProcess\" title=\"设为已处理\" onclick=\"javascript:setProcess('"+ntfkey+"','"+time+"')\">设为已处理</button>");
	}
	else{
		sb.append("&nbsp;&nbsp;<button type=\"button\" class=\"btn btn-SetProcess\" title=\"设为已处理\" onclick=\"javascript:setProcess('"+ntfkey+"','"+time+"')\">设为已处理</button>");
	}
	sb.append("<div class=\"icon-signout icon-myout\" onclick=\"javascript:closeDescDiv()\"></div>");
	sb.append( "</div>");

	if(null == datas){
		return;
	}

	var nodeAttr ={
		"ip":{"n":"IP","v":mainObj['ip']},
		"host":{"n":"HOST","v":mainObj['host']},
		"title":{"n":"问题摘要","v":mainObj['title']},
		"eventid":{"n":"事件类型","v":mainObj['eventid']},
		"count":{"n":"报警次数","v":datas.length},
		"state":{"n":"状态","v":""},
		"viewTs":{"n":"预警浏览时间","v":mainObj['view_ts'] ? TimeHelper.getTime(mainObj['view_ts'],'FMS'): ""},
		"processTs":{"n":"预警处理时间","v":mainObj['process_ts'] ? TimeHelper.getTime(mainObj['process_ts'],'FMS') : ""},
		"retry":{"n":"触发报警动作次数(邮件/短信)","v":mainObj['retry']},
		"latTs":{"n":"最近触发报警动作时间","v":mainObj['latest_ts'] ? TimeHelper.getTime(mainObj['latest_ts'],'FMS') : ""}
	}
	
	setState(nodeAttr,mainObj['state']);
	
	/**
	 * 渲染容器
	 */
	sb.append("<div class=\"publicheadDiv\">");
	sb.append("<ul>");
	sb.append("<li><span >"+nodeAttr.ip.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.ip.v+"("+nodeAttr.host.v+")</span></li>");
	sb.append( "<li><span >"+nodeAttr.title.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.title.v+"</span></li>");
	sb.append( "<li><span >"+nodeAttr.eventid.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.eventid.v+"</span></li>");
	sb.append("<li><span >"+nodeAttr.count.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.count.v+"</span></li>");
	sb.append("<li><span >"+nodeAttr.state.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.state.v+"</span></li>");
	sb.append("<li><span >"+nodeAttr.viewTs.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.viewTs.v+"</span></li>");
	if(nodeAttr.processTs.v != ""){
		sb.append("<li><span >"+nodeAttr.processTs.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.processTs.v+"</span></li>");
	}
	sb.append("<li><span >"+nodeAttr.retry.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.retry.v+"</span></li>");
	sb.append("<li><span >"+nodeAttr.latTs.n+"</span><span class=\"colon\">：</span><span class=\"tValue\">"+nodeAttr.latTs.v+"</span></li>");
	
	
	sb.append( "</ul>");
	sb.append( "</div>");
	
	var appendObj =  HtmlHelper.id(appendId);
	appendObj.innerHTML=sb.toString();
	sb=new StringBuffer();
	
	var display = "block";
	var index=1;
	$.each(datas,function(id,obj){
		
		var objattr ={
				"time":{"n":"预警时间","v":TimeHelper.getTime(obj['time'],'FMS')},	
				"nodename":{"n":"UAV节点进程","v":obj['args']['nodename']},
				"nodeuuid":{"n":"UAV节点ID","v":obj['args']['nodeuuid']},
				"component":{"n":"报警组件","v":obj['args']['component']},
				"feature":{"n":"报警组件Feature","v":obj['args']['feature']},
				"strategydesc":{"n":"报警策略","v":obj['args']['strategydesc']},
				"desc":{"n":"问题描述","v":obj['description']}
			};

			
		/**
		 * 如果args没有映射，则自动填充属性和值显示
		 */
		var autoAddHtml = "";
		$.each(obj['args'],function(index,value){
			if(!nodeAttr[index] && !objattr[index]){
				autoAddHtml += "<li><span class=\"argsSubTitle\">"+index+"</span><span class=\"colon\">：</span><span class='argsSubValue'>"+value+"</span></li>";
			}
		});
				
		var issueDesc=objattr.desc.v.replace(/\n/g,"<br/>");

		sb.append(" <ul>");
		sb.append(" <div class=\"listDiv\">");
		
		sb.append("	<li onclick=\"javascript:showDesc(this)\" class=\"title\" >["+index+"]&nbsp;"+objattr.time.v+"</li>");
		sb.append( " <div style=\"display:"+display+";\">");
		sb.append( " <li><span class=\"argsTitle\">来源</span></li>");
		sb.append("	<li><span class=\"argsSubTitle\">"+objattr.nodename.n+"</span></span><span class=\"colon\">：</span><span>"+objattr.nodename.v+"("+objattr.nodeuuid.v+")</span></li>");
		sb.append( "<li><span class=\"argsSubTitle\" >"+objattr.component.n+"</span><span class=\"colon\">：</span><span>"+objattr.feature.v+"."+objattr.component.v+"</span></li>");
		sb.append( "<li><span class=\"argsSubTitle\" >"+objattr.strategydesc.n+"</span><span class=\"colon\">：</span><span>"+objattr.strategydesc.v+"</span></li>");
		sb.append( " <li><span class=\"argsTitle\">"+objattr.desc.n+"</span><div class=\"listBdesc\">"+issueDesc+"</div></li>");
		sb.append( " <div class=\"args\">" /*用于后续扩充收缩功能*/);
		sb.append( "<li class=\"argsTitle\"><span>上下文信息</span></li>");
		sb.append(autoAddHtml);
		sb.append( " </div>");
		sb.append( "</div>");
		sb.append( "</div>");
		sb.append( "</ul>");
		
		display = "none";
		
		index++;
	});
	
	appendObj.innerHTML+=sb.toString();
	
}

function setState(nodeAttr,state){
	if(0==state){
		nodeAttr.state.v="新预警";
	}else if(10==state){
		nodeAttr.state.v="报警持续中";
	}else if(15==state){
		nodeAttr.state.v="已查看";
	}else if(state==20){
		nodeAttr.state.v="已查看&报警持续中";
	}else if(state==25){
		nodeAttr.state.v="已处理";
	}
}

function showDesc(obj){
	var objDiv = obj.parentNode;
	var listbodyDiv = objDiv.getElementsByTagName("div")[0];
	if("block" == listbodyDiv.style.display){
		listbodyDiv.style.display = "none";
	}else{
		listbodyDiv.style.display = "block";
	}
}

function setProcess(ntfkey,time){
	var paramObject = {"action":"process","ntfkey":ntfkey,"time":time,"type":"mgr"};
	updateNotify_RestfulClient(paramObject);
}