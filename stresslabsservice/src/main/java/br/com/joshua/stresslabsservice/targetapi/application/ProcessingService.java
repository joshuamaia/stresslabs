package br.com.joshua.stresslabsservice.targetapi.application;

import org.springframework.stereotype.Service;

import br.com.joshua.stresslabsservice.targetapi.domain.ProcessingWorkload;
import br.com.joshua.stresslabsservice.targetapi.domain.ProcessingWorkload.ProcessingResult;

@Service
public class ProcessingService {

    public ProcessingResult process(int workUnits) {
        return ProcessingWorkload.execute(workUnits);
    }
}
