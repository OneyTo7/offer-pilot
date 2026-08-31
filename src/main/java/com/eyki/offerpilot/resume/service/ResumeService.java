package com.eyki.offerpilot.resume.service;

import com.eyki.offerpilot.resume.dto.ResumeDetailVO;
import com.eyki.offerpilot.resume.dto.ResumeVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResumeService {

    ResumeVO upload(MultipartFile file, String name);

    ResumeDetailVO getDetail(Long id);

    List<ResumeVO> listMyResumes();

    void delete(Long id);

    void setDefault(Long id);
}