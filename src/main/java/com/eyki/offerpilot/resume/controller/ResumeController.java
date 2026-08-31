package com.eyki.offerpilot.resume.controller;

import com.eyki.offerpilot.common.model.ApiResult;
import com.eyki.offerpilot.resume.dto.ResumeDetailVO;
import com.eyki.offerpilot.resume.dto.ResumeVO;
import com.eyki.offerpilot.resume.service.ResumeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ResumeVO> upload(@RequestParam("file") MultipartFile file,
        @RequestParam(value = "name", required = false) String name) {
        ResumeVO resume = resumeService.upload(file, name);
        return ApiResult.success("上传成功", resume);
    }

    @GetMapping
    public ApiResult<List<ResumeVO>> listMyResumes() {
        List<ResumeVO> resumes = resumeService.listMyResumes();
        return ApiResult.success(resumes);
    }

    @GetMapping("/{id}")
    public ApiResult<ResumeDetailVO> getDetail(@PathVariable Long id) {
        ResumeDetailVO detail = resumeService.getDetail(id);
        return ApiResult.success(detail);
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return ApiResult.success("删除成功");
    }

    @PutMapping("/{id}/default")
    public ApiResult<?> setDefault(@PathVariable Long id) {
        resumeService.setDefault(id);
        return ApiResult.success("设置成功");
    }
}