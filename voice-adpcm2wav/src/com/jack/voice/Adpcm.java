package com.jack.voice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Adpcm {

//	# table of  quantizer step size
	int[] stepSizeTable = {16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41,
	                 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173,
	                 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658,
	                 724, 796, 876, 963, 1060, 1166, 1282, 1408, 1552};
	// 另外的一个 stepsizeTable ，区别在哪？需要研究下
	private static int[] stepsizeTable = {
	           7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
	           19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
	           50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
	           130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
	           337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
	           876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
	           2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
	           5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
	           15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767};
	
//	# table of index
	private static int[] indexTable = {-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8};
//	# ADPCM_Encode.
//	# sample: a 16-bit PCM sample
//	# retval : a 4-bit ADPCM sample
	int predsample = 0;
	int index = 0;
	
	/**
	 * ADPCM 编码	
	 * 翻译c语言的实现，这部分没有验证过
	 * @param sample
	 * @return
	 */
	public byte ADPCM_Encode(int sample){
	    int code = 0;
	    int step_size = stepSizeTable[index];

//	    # compute diff and record sign and absolut value
	    int diff = sample - predsample;
	    if (diff < 0){
	    	code = 8;
	    	diff = -diff;
	    }
	        
//	    # quantize the diff into ADPCM code
//	    # inverse quantize the code into a predicted diff
	    int tmpstep = step_size;
	    int diffq = step_size >> 3;

	    if (diff >= tmpstep){
	    	code = code | 0x04;
	    	diff -= tmpstep;
	    	diffq = diffq + step_size;
	    }

	    tmpstep = tmpstep >> 1;

	    if (diff >= tmpstep){
	    	code = code | 0x02;
	    	diff = diff - tmpstep;
	    	diffq = diffq + (step_size >> 1);
	    }

	    tmpstep = tmpstep >> 1;

	    if (diff >= tmpstep){
	    	code = code | 0x01;
	    	diffq = diffq + (step_size >> 2);
	    }

//	    # fixed predictor to get new predicted sample
	    if ((code & 8)!=0){
	    	predsample = predsample - diffq;
	    } else {
	    	predsample = predsample + diffq;
	    }
	        
//	    # check for overflow
	    if (predsample > 32767){
	    	predsample = 32767;
	    }else if(predsample < -32768){
	    	predsample = -32768;
	    }	        

//	    # find new stepsize index
	    index += indexTable[code];

//	    # check for overflow
	    if (index < 0)
	        index = 0;

	    if (index > 48)
	        index = 48;

//	    # return new ADPCM code   code & 0x0f == code
	    return (byte)(code & 0x0f);//返回低4位字节的
	}
	
//	# ADPCM_Decode.
//	# code: a byte containing a 4-bit ADPCM sample.
//	# retval : 16-bit ADPCM sample
	int de_index = 0;
	int de_predsample = 0;
	
	/**
	 * ADPCM 解码   4Bit --> 16Bit
	 * @param code 1byte(包括一个4bit的ADPCM采样数据)
	 * @return short(一个16bit的PCM采样数据)
	 */
	public short ADPCM_Decode(int code){
	    int step_size = stepSizeTable[de_index];

//	    # inverse code into diff
	    int diffq = step_size >> 3;  //# == step/8
	    if ((code & 4) != 0){
	        diffq += step_size;
	    }

	    if ((code & 2) != 0){
	    	diffq += step_size >> 1;
	    }	        

	    if ((code & 1)!=0){
	    	diffq += step_size >> 2;
	    }	        

//	    # add diff to predicted sample
	    if ((code & 8)!=0){
	    	diffq = -diffq;
	    }	        

	    de_predsample += diffq;

//	    # check for overflow  clip the values to +/- 2^11 (supposed to be 16 bits)
	    if (de_predsample > 2047){
	        de_predsample = 2047;
	    }else if (de_predsample < -2048){
	        de_predsample = -2048;
	    }

//	    # find new quantizer step size
	    de_index += indexTable[code];

//	    # check for overflow
	    if (de_index < 0){
	        de_index = 0;
	    }
	    if (de_index > 48){
	        de_index = 48;
	    }
//	    # save predict sample and de_index for next iteration
//	    # return new decoded sample
//	    # The original algorithm turned out to be 12bit, need to convert to 16bit
	    return (short)(de_predsample << 4);
	}
	/**
	 * 
	 * ADPCM 转换为  WAV: vox(ADPCM,没有Header的音频数据) to wav(PCM)
	 * @param voxName 8000HZ 单通道 4bit	ADPCM  案例使用的是voiceData下的 win7_4bit_8k_mono.adpcm
	 * @param wavName 8000HZ 单通道 16bit	PCM    案例生成的是voiceData下的decode_16bit_8k_mono.wav
	 * @param path
	 * @param endian True : big-endian（暂时不可以用）  false : little-endian
	 */
	public void convertAdpcmToWav(String voxName,String wavName,String path, boolean isBigEndian){
		File in = new File(path+voxName);
		File out = new File(path+wavName);
		try {
			FileInputStream fis = new FileInputStream(in);
			if(!out.exists()){
            	out.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(out);
            int length =fis.available();
            Header header = new Header(1, 8000, 16); 
            byte[] head = header.writeHeard(length,"PCM");
            assert head.length == 44;
            fos.write(head);
			int len = fis.available();
			byte[] list_8bit_bytes = new byte[len];
            int rLen = fis.read(list_8bit_bytes, 0, len);
            fis.close();
            //特殊注释，将这两个作为未压缩数据 进行解压，也能够正常播报
            /**
             * 如果将以下数据打开，则head中的该注释也需要打开
             * 这说明wave文件在识别是否是正确格式时，主要对照的heade的参数以及具体文件的存储格式
             * 目前，猜测，这两个字节数据应该不是未压缩数据。如果强制按照未压缩数据处理，最后文件中应该是噪音数据（因为采用较少，人耳无法感知）
             */
//            fos.write(list_8bit_bytes[1]);
//            fos.write(list_8bit_bytes[0]);
//            for(int i=2;i<len;i++){
            //特殊注释，end
            for(int i=0;i<len;i++){
                byte byte_i = list_8bit_bytes[i];  //# 1 bytes = 8bit
                int high_4bit = (byte_i & 0xf0) >> 4; // # split high 4bit from 8bit
            	int low_4bit = byte_i & 0x0f; // # split low 4bit from 8bit
                //now decode
                short tmpDeS16_0 = ADPCM_Decode(high_4bit);
                short tmpDeS16_1 = ADPCM_Decode(low_4bit);
                if(isBigEndian){
                	fos.write(Header.HexByteBigEndian(tmpDeS16_0,2));
                	fos.write(Header.HexByteBigEndian(tmpDeS16_1,2));
                }else{
                	fos.write(Header.HexByteLittleEndian(tmpDeS16_0,2));
                    fos.write(Header.HexByteLittleEndian(tmpDeS16_1,2));
                }
            }
            fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	
	}

	/**
	 * wave格式的 PCM 转换为 IMA-ADPCM
	 * @param pcmName 	8000HZ 单通道 16bit	PCM    案例使用的是voiceData下的  win7_16bit_8k_mono.wav
	 * @param adpcmName 8000HZ 单通道 4bit		ADPCM  案例生成的是voiceData下的  encode_4bit_8k_mono.wav
	 * @param path
	 * @param isBigEndian
	 */
	public void convertWavePCMToADPCM(String pcmName, String adpcmName, String path, boolean isBigEndian) {
		File in = new File(path + pcmName);
		File out = new File(path + adpcmName);
		try {
			FileInputStream fis = new FileInputStream(in);
			if (!out.exists()) {
				out.createNewFile();
			}
			FileOutputStream fos = new FileOutputStream(out);
			long length = in.length();
			long sampleNum = (long) Math.ceil((length-44)/2);
			Header header = new Header(1, 8000, 4);
			byte[] head = header.writeHeard(sampleNum, "ADPCM");
			assert head.length == 60;
			fos.write(head);
			BlockHeader blockheard = new BlockHeader();
			// 设置默认的index和rsv
			blockheard.setBLOCKIndex(0);
			blockheard.setBLOCKRSV(0);
			byte[] headBytes = new byte[2];
			byte[] inData = new byte[1008];
			// 读取前44个字节的PCM编码的WAV头 丢掉
			fis.read(inData, 0, 44);
			
			// 计算包含的 block块的个数（不足一块的向上取整）
			int blockNum = (int) ((length - 44) / 1010);
			if ((length - 44) % 1010 >= 2) {// 至少要包含一个采样数据（2字节）用来做header
				blockNum += 1;
			}
			
			// 设置循环变量
			int len = 0;
			int currentBlockIndex = 1;
			while (currentBlockIndex <= blockNum) {
				// 组织header 数据 START
				len = fis.read(headBytes, 0, 2);// 每个ADPCM 块开头的一个未压缩数据
				blockheard.setBLOCKPresample((headBytes[0] & 0xff) | headBytes[1] << 8);
				// blockheard 的 4 bytes()
				fos.write(Header.HexByteLittleEndian(blockheard.getBLOCKPresample(), 2));
				fos.write(Header.HexByteLittleEndian(blockheard.getBLOCKIndex(), 1));
				fos.write(Header.HexByteLittleEndian(blockheard.getBLOCKRSV(), 1));
				// blockheard 的 4 bytes()
				// 组织header 数据 END

				// blockData 的 252 bytes() START
				if (currentBlockIndex < blockNum) {
					len = fis.read(inData);// 压缩比为4:1，可转换为1008/4=252个字节
				} else if (currentBlockIndex == blockNum && (length - 44) % 1010 > 2) {
					inData = new byte[(int) ((length - 44) % 1010 - 2)];
					len = fis.read(inData);// 读取长度需要减去 2字节的head数据
				} else {
					break;
				}
				if (len != -1) {
					CoderRealize(fos, inData, len, blockheard);
				} else {
					break;
				}
				// blockData 的 252 bytes() END

				currentBlockIndex++;

			}
			fis.close();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * wave格式的 PCM 转换为 IMA-ADPCM
	 * @param adpcmName 8000HZ 单通道 4bit		ADPCM  案例生成的是voiceData下的  wave_4bit_8k_mono.wav
	 * @param pcmName 	8000HZ 单通道 16bit	PCM    案例使用的是voiceData下的  decode_16bit_8k_mono1.wav
	 * @param path
	 * @param isBigEndian
	 */
	public void convertWaveADPCMToPCM(String adpcmName, String pcmName, String path, boolean isBigEndian) {
		File in = new File(path + adpcmName);
		File out = new File(path + pcmName);
		try {
			FileInputStream fis = new FileInputStream(in);
			if (!out.exists()) {
				out.createNewFile();
			}
			FileOutputStream fos = new FileOutputStream(out);
			long length = in.length();
			long nBlock = length / 256;
			long sampleNum = nBlock * 505;//每个block块包含505个采样
			long otherBytes = length % 256;
			if (otherBytes != 0) {
				sampleNum += (otherBytes - 4) * 2 + 1;
			}
			//long sampleNum = (long) Math.ceil((length - 44) / 2);
			Header header = new Header(1, 8000, 16);
			//byte[] head = header.writeHeard(sampleNum, "PCM");
			byte[] head = header.writeHeard((long) Math.ceil(sampleNum / 2), "PCM");
			assert head.length == 60;
			fos.write(head);
			// 根据长度信息 组织 目标文件的 头信息
			BlockHeader blockheard = new BlockHeader();
			byte[] inData = new byte[1024];
			// 丢掉 WAVE IMA-ADPCM 的 头信息
			fis.read(inData, 0, 60);
			int len;
			while ((len = fis.read(inData, 0, 256)) != -1) {
				blockheard.setBLOCKPresample(inData[0] | inData[1] << 8);
				blockheard.setBLOCKIndex((int) inData[2]);
				blockheard.setBLOCKRSV((int) inData[3]);
				fos.write(Header.HexByteLittleEndian(blockheard.getBLOCKPresample(), 2));
				deCoderRealize(fos, inData, len, blockheard);
			}
			fis.close();
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	private static void CoderRealize(FileOutputStream fos, byte[] inData, int len, BlockHeader blockheard) {
	       try {
	           int sign;
	           int delta;
	           int step;
	           int valpred;
	           int vpdiff;
	           int diff;
	           int index;
	           boolean bufferstep;
	           int outputbuffer = 0;

	           valpred = blockheard.getBLOCKPresample();
	           index = blockheard.getBLOCKIndex();
	           step = stepsizeTable[index];
	           bufferstep = true;
	           for (int i = 0; i < len / 2; i++) {
	               int val = (inData[i * 2] & 0xff) | inData[i * 2 + 1] << 8 ;
	                   diff = val - valpred;
	                   sign = (diff < 0) ? 8 : 0;
	                   if (sign != 0) {
	                       diff = (-diff);
	                   }
	                   delta = 0;
	                   vpdiff = (step >> 3);

	                   if (diff >= step) {
	                       delta = 4;
	                       diff -= step;
	                       vpdiff += step;
	                   }
	                   step >>= 1;
	                   if (diff >= step) {
	                       delta |= 2;
	                       diff -= step;
	                       vpdiff += step;
	                   }
	                   step >>= 1;
	                   if (diff >= step) {
	                       delta |= 1;
	                       vpdiff += step;
	                   }
	                   if (sign != 0)
	                       valpred -= vpdiff;
	                   else
	                       valpred += vpdiff;
	                   if (valpred > 32767)
	                       valpred = 32767;
	                   else if (valpred < -32768)
	                       valpred = -32768;
	                   delta |= sign;

	                   index += indexTable[delta];
	                   if (index < 0) index = 0;
	                   if (index > 88) index = 88;
	                   step = stepsizeTable[index];
	                   if (bufferstep) {
	                       outputbuffer = delta & 0x0f;
	                   } else {
	                       fos.write((byte) ((delta << 4) & 0xf0) | outputbuffer);
	                       //System.out.println("i:"+i);
	                   }
	                   bufferstep = !bufferstep;
	           }
	           if (!bufferstep) {
	               fos.write(outputbuffer & 0xff);
	               System.out.println("end");
	           }
	           blockheard.setBLOCKIndex(index);
	       } catch (IOException e) {
	           e.printStackTrace();
	       } catch (Exception e) {
	           e.printStackTrace();
	       }

	   }
	
	private static void deCoderRealize(FileOutputStream fos, byte[] inData, int len, BlockHeader blockheard) {
		int sign;
		int delta;
		int step;
		int valpred;
		int vpdiff;
		int index;
		boolean bufferstep;
		int j = 4;
		valpred = blockheard.getBLOCKPresample();
		index = blockheard.getBLOCKIndex();
		if (index < 0) {
			index %= 89;
			index = index + 88 + 1;
		} else if (index > 88) {
			index %= 89;
		}
		step = stepsizeTable[index];
		bufferstep = true;
		for (int i = 0; i < (len - 4) * 2; i++) {
			try {
				if (bufferstep) {
					// 取低四位
					delta = inData[j] & 0xf;
				} else {
					// 取高四位
					delta = (inData[j] >> 4) & 0xf;
					j++;
				}
				bufferstep = !bufferstep;
				index += indexTable[delta];
				if (index < 0)
					index = 0;
				if (index > 88)
					index = 88;
				// 取符号位 &（与运算）都为1才为1
				sign = delta & 8;// sing只会等于8或者0
				// 取数据
				delta = delta & 7;
				// 下边四则运算将vpdiff = (delta+0.5)*step/4四则运算转换成了二进制的与或运算（牛逼）
				vpdiff = step >> 3;
				if ((delta & 4) != 0) {
					vpdiff += step;
				}
				if ((delta & 2) != 0) {
					vpdiff += step >> 1;
				}
				if ((delta & 1) != 0) {
					vpdiff += step >> 2;
				}
				if (sign != 0) {
					valpred -= vpdiff;
				} else {
					valpred += vpdiff;
				}
				if (valpred > 32767)
					valpred = 32767;
				else if (valpred < -32768)
					valpred = -32768;
				step = stepsizeTable[index];
				fos.write(Header.HexByteLittleEndian(valpred, 2));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 *  解码的案例
	 * @param args
	 */
	public static void main(String[] args) {
		Adpcm a = new Adpcm();
		//解码程序 Test START
//		a.convertAdpcmToWav("win7_4bit_8k_mono.adpcm", "decode_16bit_8k_mono.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		//打开特殊注释的转换结果
//		a.convertAdpcmToWav("win7_4bit_8k_mono.adpcm", "decode_16bit_8k_mono_1.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		
		a.convertWaveADPCMToPCM("wave_4bit_8k_mono.wav", "decode_16bit_8k_mono1.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		//解码程序 Test END
		
		//编码程序 Test START
//		a.convertWavePCMToADPCM("win7_16bit_8k_mono.wav", "encode_4bit_8k_mono.wav", "E:\\workSpace\\ADPCMVoice\\voice\\",false);
		//编码程序 Test END
	}
	
}
