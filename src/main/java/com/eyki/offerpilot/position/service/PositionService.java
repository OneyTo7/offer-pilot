package com.eyki.offerpilot.position.service;

import com.eyki.offerpilot.position.dto.PositionRequest;
import com.eyki.offerpilot.position.dto.PositionVO;

import java.util.List;

public interface PositionService {

    PositionVO create(PositionRequest request);

    PositionVO getDetail(Long id);

    PositionVO update(Long id, PositionRequest request);

    List<PositionVO> listMyPositions();

    void delete(Long id);

    void setDefault(Long id);
}