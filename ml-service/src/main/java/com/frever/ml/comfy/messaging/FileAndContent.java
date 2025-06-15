package com.frever.ml.comfy.messaging;

import java.io.InputStream;

public record FileAndContent(String fileName, InputStream fileContent) {
}
