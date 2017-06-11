/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-SparkML
 *
 * JPMML-SparkML is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-SparkML is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-SparkML.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.sparkml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.spark.ml.Model;
import org.apache.spark.ml.PredictionModel;
import org.apache.spark.ml.classification.ClassificationModel;
import org.apache.spark.ml.param.shared.HasFeaturesCol;
import org.apache.spark.ml.param.shared.HasLabelCol;
import org.apache.spark.ml.param.shared.HasPredictionCol;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Output;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.Schema;

abstract
public class ModelConverter<T extends Model<T> & HasFeaturesCol & HasPredictionCol> extends TransformerConverter<T> {

	public ModelConverter(T model){
		super(model);
	}

	abstract
	public MiningFunction getMiningFunction();

	abstract
	public org.dmg.pmml.Model encodeModel(Schema schema);

	public Schema encodeSchema(SparkMLEncoder encoder){
		T model = getTransformer();

		Label label = null;

		if(model instanceof HasLabelCol){
			HasLabelCol hasLabelCol = (HasLabelCol)model;

			String labelCol = hasLabelCol.getLabelCol();

			Feature feature = encoder.getOnlyFeature(labelCol);

			MiningFunction miningFunction = getMiningFunction();
			switch(miningFunction){
				case CLASSIFICATION:
					{
						DataField dataField;

						if(feature instanceof CategoricalFeature){
							CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

							dataField = encoder.getDataField(categoricalFeature.getName());
						} else

						if(feature instanceof ContinuousFeature){
							ContinuousFeature continuousFeature = (ContinuousFeature)feature;

							int numClasses = 2;

							if(model instanceof ClassificationModel){
								ClassificationModel<?, ?> classificationModel = (ClassificationModel<?, ?>)model;

								numClasses = classificationModel.numClasses();
							}

							List<String> categories = new ArrayList<>();

							for(int i = 0; i < numClasses; i++){
								categories.add(String.valueOf(i));
							}

							dataField = encoder.toCategorical(continuousFeature.getName(), categories);

							CategoricalFeature categoricalFeature = new CategoricalFeature(encoder, dataField);

							encoder.putFeatures(labelCol, Collections.<Feature>singletonList(categoricalFeature));
						} else

						{
							throw new IllegalArgumentException("Expected a categorical or categorical-like continuous feature, got " + feature);
						}

						label = new CategoricalLabel(dataField);
					}
					break;
				case REGRESSION:
					{
						DataField dataField = encoder.toContinuous(feature.getName());

						dataField.setDataType(DataType.DOUBLE);

						label = new ContinuousLabel(dataField);
					}
					break;
				default:
					throw new IllegalArgumentException("Mining function " + miningFunction + " is not supported");
			}
		}

		if(model instanceof ClassificationModel){
			ClassificationModel<?, ?> classificationModel = (ClassificationModel<?, ?>)model;

			CategoricalLabel categoricalLabel = (CategoricalLabel)label;

			int numClasses = classificationModel.numClasses();
			if(numClasses != categoricalLabel.size()){
				throw new IllegalArgumentException("Expected " + numClasses + " target categories, got " + categoricalLabel.size() + " target categories");
			}
		}

		HasFeaturesCol hasFeaturesCol = (HasFeaturesCol)model;

		String featuresCol = hasFeaturesCol.getFeaturesCol();

		List<Feature> features = encoder.getFeatures(featuresCol);

		if(model instanceof PredictionModel){
			PredictionModel<?, ?> predictionModel = (PredictionModel<?, ?>)model;

			int numFeatures = predictionModel.numFeatures();
			if(numFeatures != -1 && features.size() != numFeatures){
				throw new IllegalArgumentException("Expected " + numFeatures + " features, got " + features.size() + " features");
			}
		}

		Schema result = new Schema(label, features);

		return result;
	}

	public Output encodeOutput(Label label, SparkMLEncoder encoder){
		return null;
	}

	public org.dmg.pmml.Model registerModel(SparkMLEncoder encoder){
		Schema schema = encodeSchema(encoder);

		Label label = schema.getLabel();

		Output output = encodeOutput(label, encoder);

		org.dmg.pmml.Model model = encodeModel(schema)
			.setOutput(output);

		return model;
	}
}