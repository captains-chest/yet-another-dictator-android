package com.captainduckay.dictator.android

import com.captainduckay.dictator.core.RecognizerSetup
import com.captainduckay.dictator.core.SpeechModelId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class RecognizerConfigTest {
    private val folder = File("/models/x")

    @Test fun parakeet110MLoadsAsNemoCtc() {
        val config = recognizerConfig(RecognizerSetup.of(SpeechModelId.PARAKEET_110M), folder, threads = 4)
        val model = config.modelConfig
        assertEquals("nemo_ctc", model.modelType)
        assertEquals("/models/x/model.int8.onnx", model.nemo.model)
        assertEquals("/models/x/tokens.txt", model.tokens)
        assertEquals(4, model.numThreads)
        // Not a transducer: those fields stay empty, so sherpa-onnx picks the CTC path.
        assertEquals("", model.transducer.encoder)
        assertEquals("", model.transducer.decoder)
        assertEquals("", model.transducer.joiner)
        assertEquals("greedy_search", config.decodingMethod)
        assertEquals(16_000, config.featConfig.sampleRate)
        assertEquals(80, config.featConfig.featureDim)
    }

    @Test fun parakeet06BStaysATransducer() {
        val config = recognizerConfig(RecognizerSetup.of(SpeechModelId.PARAKEET_V2), folder, threads = 2)
        val model = config.modelConfig
        assertEquals("nemo_transducer", model.modelType)
        assertEquals("/models/x/encoder.int8.onnx", model.transducer.encoder)
        assertEquals("/models/x/decoder.int8.onnx", model.transducer.decoder)
        assertEquals("/models/x/joiner.int8.onnx", model.transducer.joiner)
        assertEquals("", model.nemo.model)
    }
}
