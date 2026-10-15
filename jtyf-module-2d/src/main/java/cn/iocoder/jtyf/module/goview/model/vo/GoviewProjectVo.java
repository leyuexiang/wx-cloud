package cn.iocoder.jtyf.module.goview.model.vo;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Date;

public class GoviewProjectVo implements Serializable {
    private static final long serialVersionUID = 1L;

	
	@Schema(description = "主键")
	private String id;
	
	@Schema(description = "项目名称")
	private String projectName;
	
	@Schema(description = "项目状态[-1未发布,1发布]")
	private Integer state;
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone="GMT+8")
	@Schema(description = "创建时间")
	private Date createTime;
	
	@Schema(description = "创建人id")
	private String createUserId;
	
	@Schema(description = "删除状态[1删除,-1未删除]")
	private Integer isDelete;
	
	@Schema(description = "首页图片")
	private String indexImage;
	
	@Schema(description = "项目介绍")
	private String remarks;
	
	private String content;
	
	
	@JsonProperty("id")
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id =  id;
	}
	@JsonProperty("projectName")
	public String getProjectName() {
		return projectName;
	}

	public void setProjectName(String projectName) {
		this.projectName =  projectName;
	}
	@JsonProperty("state")
	public Integer getState() {
		return state;
	}

	public void setState(Integer state) {
		this.state =  state;
	}
	@JsonProperty("createTime")
	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime =  createTime;
	}
	@JsonProperty("createUserId")
	public String getCreateUserId() {
		return createUserId;
	}

	public void setCreateUserId(String createUserId) {
		this.createUserId =  createUserId;
	}
	@JsonProperty("isDelete")
	public Integer getIsDelete() {
		return isDelete;
	}

	public void setIsDelete(Integer isDelete) {
		this.isDelete =  isDelete;
	}
	@JsonProperty("indexImage")
	public String getIndexImage() {
		return indexImage;
	}

	public void setIndexImage(String indexImage) {
		this.indexImage =  indexImage;
	}
	@JsonProperty("remarks")
	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		this.remarks =  remarks;
	}
	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String dateToStringConvert(Date date) {
		if(date!=null) {
			return DateUtil.format(date, "yyyy-MM-dd HH:mm:ss");
		}
		return "";
	}
}