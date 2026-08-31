package com.eyki.offerpilot.position.controller;

import com.eyki.offerpilot.common.model.ApiResult;
import com.eyki.offerpilot.position.dto.PositionRequest;
import com.eyki.offerpilot.position.dto.PositionVO;
import com.eyki.offerpilot.position.service.PositionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @PostMapping
    public ApiResult<PositionVO> create(@Valid @RequestBody PositionRequest request) {
        PositionVO position = positionService.create(request);
        return ApiResult.success("创建成功", position);
    }

    @GetMapping
    public ApiResult<List<PositionVO>> listMyPositions() {
        List<PositionVO> positions = positionService.listMyPositions();
        return ApiResult.success(positions);
    }

    @GetMapping("/{id}")
    public ApiResult<PositionVO> getDetail(@PathVariable Long id) {
        PositionVO position = positionService.getDetail(id);
        return ApiResult.success(position);
    }

    @PutMapping("/{id}")
    public ApiResult<PositionVO> update(@PathVariable Long id, @Valid @RequestBody PositionRequest request) {
        PositionVO position = positionService.update(id, request);
        return ApiResult.success("更新成功", position);
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        positionService.delete(id);
        return ApiResult.success("删除成功");
    }

    @PutMapping("/{id}/default")
    public ApiResult<?> setDefault(@PathVariable Long id) {
        positionService.setDefault(id);
        return ApiResult.success("设置成功");
    }
}